package com.mars.linker.broker.ui.logstream;

import com.mars.linker.broker.ui.config.MarsLinkerUiProperties;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.util.concurrent.DefaultThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(prefix = "mars.linker.ui", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LogStreamServer implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(LogStreamServer.class);

    private volatile boolean running;
    private volatile Channel serverChannel;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    private final int port;
    private final MarsLinkerUiProperties uiProperties;

    public LogStreamServer(@Value("${mars.linker.ui.log-ws-port:11886}") int port,
                           MarsLinkerUiProperties uiProperties) {
        this.port = port;
        this.uiProperties = uiProperties;
    }

    @Override
    public void start() {
        if (running) return;

        boolean useEpoll = Epoll.isAvailable();
        if (useEpoll) {
            bossGroup = new EpollEventLoopGroup(1, new DefaultThreadFactory("log-ws-boss", true));
            workerGroup = new EpollEventLoopGroup(2, new DefaultThreadFactory("log-ws-worker", true));
        } else {
            bossGroup = new NioEventLoopGroup(1, new DefaultThreadFactory("log-ws-boss", true));
            workerGroup = new NioEventLoopGroup(2, new DefaultThreadFactory("log-ws-worker", true));
        }

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(useEpoll ? EpollServerSocketChannel.class : NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 128)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline()
                                .addLast("http-codec", new HttpServerCodec())
                                .addLast("http-aggregator", new HttpObjectAggregator(65536))
                                .addLast("ws-protocol", new WebSocketServerProtocolHandler("/api/ui/logs/stream", null, true))
                                .addLast("ws-handler", new LogStreamWsHandler(uiProperties));
                    }
                });

        serverChannel = bootstrap.bind(port).syncUninterruptibly().channel();
        running = true;
        log.info("Log stream WebSocket server listening on ws://localhost:{}/api/ui/logs/stream", port);
    }

    @Override
    public void stop() {
        if (!running) return;
        running = false;
        try {
            if (serverChannel != null) {
                serverChannel.close().syncUninterruptibly();
            }
        } finally {
            if (bossGroup != null) {
                bossGroup.shutdownGracefully(0, 3, TimeUnit.SECONDS);
            }
            if (workerGroup != null) {
                workerGroup.shutdownGracefully(0, 3, TimeUnit.SECONDS);
            }
        }
        log.info("Log stream WebSocket server stopped");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 1;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }
}
