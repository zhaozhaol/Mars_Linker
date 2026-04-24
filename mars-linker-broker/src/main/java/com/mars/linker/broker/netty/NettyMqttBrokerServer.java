package com.mars.linker.broker.netty;

import com.mars.linker.broker.config.MarsLinkerMqttBrokerProperties;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.util.concurrent.DefaultThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.io.File;
import com.mars.linker.broker.netty.acl.AclProviderFactory;
import com.mars.linker.broker.netty.auth.AuthProviderFactory;
import com.mars.linker.broker.netty.store.RetainStore;
import com.mars.linker.broker.netty.store.SessionService;
import com.mars.linker.broker.netty.store.SessionStore;
import com.mars.linker.broker.netty.store.StoreFactory;

/**
 * Netty TCP MQTT 监听入口（Spring {@link SmartLifecycle}，应用启动时 bind，停止时优雅关闭）。
 * <p>
 * 管道见 {@link MqttTcpChannelInitializer}：{@link MqttFrameDecoder} → {@link MqttProtocolHandler}。
 * 开关：{@code MarsLinker.mqtt.broker.netty-enabled=true}；端口与线程数见 {@link MarsLinkerMqttBrokerProperties}。
 * </p>
 * <p>
 * <b>测试备注</b>：本类以集成方式验证为宜（本地启动 Spring Boot，对 {@code tcpPort} 发真实 CONNECT）；
 * 单元测试通常覆盖 {@link MqttProtocolHandler} / {@link MqttFrameDecoder} 的 {@code EmbeddedChannel} 路径即可。
 * </p>
 */
@Component
@ConditionalOnProperty(name = "MarsLinker.mqtt.broker.netty-enabled", havingValue = "true")
public class NettyMqttBrokerServer implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(NettyMqttBrokerServer.class);

    private final MarsLinkerMqttBrokerProperties properties;
    private final MqttProtocolHandler mqttProtocolHandler;
    private final SessionStore sessionStore;
    private final RetainStore retainStore;

    private volatile boolean running;
    private volatile Channel serverChannel;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private final SslContext sslContext;

    public NettyMqttBrokerServer(MarsLinkerMqttBrokerProperties properties) {
        this.properties = properties;
        this.sessionStore = StoreFactory.createSessionStore(properties);
        this.retainStore = StoreFactory.createRetainStore(properties);
        StoreFactory.migrateIfNeeded(properties, this.sessionStore, this.retainStore);
        this.mqttProtocolHandler = new MqttProtocolHandler(
                AuthProviderFactory.create(properties),
                AclProviderFactory.create(properties),
                properties.isQos1RetransmitEnabled(),
                properties.getQos1RetransmitIntervalMs(),
                properties.getQos1RetransmitMaxAttempts(),
                properties.getMaxConnections(),
                properties.getInboundQos2PendingMax(),
                SessionService.create(this.sessionStore),
                this.retainStore
        );
        this.sslContext = buildServerSslContextIfNeeded(properties);
    }

    @Override
    public void start() {
        if (running) {
            return;
        }
        int boss = Math.max(1, properties.getBossThreads());
        int workers = properties.getWorkerThreads() > 0
                ? properties.getWorkerThreads()
                : Math.max(1, Runtime.getRuntime().availableProcessors()) * 2;

        // 重要：这里必须使用非 daemon 线程，否则 JVM 会在 Spring 启动完成后立刻退出（你看到的“启动后马上 stop”）。
        this.bossGroup = new NioEventLoopGroup(boss, new DefaultThreadFactory("MarsLinker-mqtt-boss", false));
        this.workerGroup = new NioEventLoopGroup(workers, new DefaultThreadFactory("MarsLinker-mqtt-io", false));

        ServerBootstrap bootstrap = new ServerBootstrap();
        int listenPort = properties.isTlsEnabled() ? properties.getTlsPort() : properties.getTcpPort();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, properties.getSoBacklog())
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childHandler(new MqttTcpChannelInitializer(properties.getMaxPacketBytes(), mqttProtocolHandler, sslContext));

        ChannelFuture bindFuture = bootstrap.bind(listenPort).syncUninterruptibly();
        this.serverChannel = bindFuture.channel();
        this.running = true;
        if (properties.isTlsEnabled()) {
            log.info("Netty MQTT TLS listening on port {} (boss={}, worker={})", listenPort, boss, workers);
        } else {
            log.info("Netty MQTT TCP listening on port {} (boss={}, worker={})", listenPort, boss, workers);
        }
    }

    @Override
    public void stop() {
        if (!running) {
            return;
        }
        running = false;
        try {
            if (serverChannel != null) {
                serverChannel.close().syncUninterruptibly();
                serverChannel = null;
            }
        } finally {
            if (bossGroup != null) {
                bossGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS).syncUninterruptibly();
                bossGroup = null;
            }
            if (workerGroup != null) {
                workerGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS).syncUninterruptibly();
                workerGroup = null;
            }
        }
        log.info("Netty MQTT TCP stopped");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    private static SslContext buildServerSslContextIfNeeded(MarsLinkerMqttBrokerProperties properties) {
        if (properties == null || !properties.isTlsEnabled()) {
            return null;
        }
        String certPath = properties.getTlsCertChainPath();
        String keyPath = properties.getTlsPrivateKeyPath();
        if (isBlank(certPath) || isBlank(keyPath)) {
            throw new IllegalStateException("tlsEnabled=true 时必须配置 tlsCertChainPath 与 tlsPrivateKeyPath");
        }
        try {
            File cert = new File(certPath);
            File key = new File(keyPath);
            String keyPassword = properties.getTlsPrivateKeyPassword();
            SslContextBuilder b = isBlank(keyPassword)
                    ? SslContextBuilder.forServer(cert, key)
                    : SslContextBuilder.forServer(cert, key, keyPassword);
            return b.build();
        } catch (Exception e) {
            throw new IllegalStateException("构建 TLS SslContext 失败: " + e.getMessage(), e);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
