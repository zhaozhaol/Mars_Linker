package com.mars.linker.broker.netty;

import com.mars.linker.broker.config.LifecycleConfigValidator;
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

import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.io.File;
import com.mars.linker.broker.netty.protocol.EventNotifyRouter;
import com.mars.linker.broker.netty.acl.AclProviderFactory;
import com.mars.linker.broker.netty.auth.AuthProviderFactory;
import com.mars.linker.broker.netty.store.RetainStore;
import com.mars.linker.broker.netty.store.SessionService;
import com.mars.linker.broker.netty.store.SessionStore;
import com.mars.linker.broker.netty.store.StoreFactory;
import com.mars.linker.broker.netty.store.BoundedRetainStore;

/**
 * Netty TCP MQTT 监听入口（Spring {@link SmartLifecycle}，应用启动时 bind，停止时优雅关闭）。
 * <p>
 * 管道见 {@link MqttTcpChannelInitializer}：{@link MqttFrameDecoder} → {@link MqttProtocolHandler}。
 * 开关：{@code mars.linker.broker.netty-enabled=true}；端口与线程数见 {@link MarsLinkerMqttBrokerProperties}。
 * </p>
 * <p>
 * <b>测试备注</b>：本类以集成方式验证为宜（本地启动 Spring Boot，对 {@code tcpPort} 发真实 CONNECT）；
 * 单元测试通常覆盖 {@link MqttProtocolHandler} / {@link MqttFrameDecoder} 的 {@code EmbeddedChannel} 路径即可。
 * </p>
 */
@Component
@ConditionalOnProperty(name = "mars.linker.broker.netty-enabled", havingValue = "true")
public class NettyMqttBrokerServer implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(NettyMqttBrokerServer.class);

    private final MarsLinkerMqttBrokerProperties properties;
    private final MqttProtocolHandler mqttProtocolHandler;
    private final com.mars.linker.broker.netty.auth.AuthProvider authProvider;
    private final com.mars.linker.broker.netty.acl.AclProvider aclProvider;
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
        RetainStore baseRetainStore = StoreFactory.createRetainStore(properties);
        StoreFactory.migrateIfNeeded(properties, this.sessionStore, baseRetainStore);
        this.retainStore = new BoundedRetainStore(
                baseRetainStore,
                properties.getRetainMaxMessages(),
                properties.getRetainTtlMs()
        );
        this.authProvider = AuthProviderFactory.create(properties);
        this.aclProvider = AclProviderFactory.create(properties);
        LifecycleConfigValidator.validate(properties);
        EventNotifyRouter eventNotifyRouter = new EventNotifyRouter(
                properties.isEventNotifyEnabled(),
                properties.getEventNotifyConfig(),
                properties.getEventNotifyDefaultForwardRules()
        );
        this.mqttProtocolHandler = new MqttProtocolHandler(
                this.authProvider,
                this.aclProvider,
                properties.isQos1RetransmitEnabled(),
                properties.getQos1RetransmitIntervalMs(),
                properties.getQos1RetransmitMaxAttempts(),
                properties.getMaxConnections(),
                properties.getInboundQos2PendingMax(),
                SessionService.create(
                        this.sessionStore,
                        properties.getSessionOfflineMaxMessages(),
                        properties.getSessionOfflineTtlMs()
                ),
                this.retainStore,
                eventNotifyRouter
        );
        this.sslContext = buildServerSslContextIfNeeded(properties);
    }

    public MqttProtocolHandler getMqttProtocolHandler() {
        return mqttProtocolHandler;
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
        if (properties.isEventNotifyEnabled()) {
            log.info("事件通知已启用：eventNotifyEnabled=true");
            if (properties.getEventNotifyConfig() != null && !properties.getEventNotifyConfig().isEmpty()) {
                log.info("已配置事件类型数量：{}", properties.getEventNotifyConfig().size());
            }
            if (properties.getEventNotifyDefaultForwardRules() == null
                    || properties.getEventNotifyDefaultForwardRules().isEmpty()) {
                log.info("未配置全局默认转发规则，未单独配置规则的事件类型将全量转发");
            } else {
                log.info("全局默认转发规则数量：{}", properties.getEventNotifyDefaultForwardRules().size());
            }
        } else {
            log.info("事件通知已关闭（标准 MQTT Broker 行为）");
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
            closeQuietly(retainStore, "retainStore");
            closeQuietly(sessionStore, "sessionStore");
            closeQuietly(aclProvider, "aclProvider");
            closeQuietly(authProvider, "authProvider");
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

    private static void closeQuietly(AutoCloseable resource, String name) {
        if (resource == null) {
            return;
        }
        try {
            resource.close();
        } catch (Exception e) {
            log.warn("关闭资源 {} 失败", name, e);
        }
    }
}
