package com.mars.linker.broker.ui.logstream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mars.linker.broker.ui.config.MarsLinkerUiProperties;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import com.nimbusds.jwt.SignedJWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

public class LogStreamWsHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    private static final Logger log = LoggerFactory.getLogger(LogStreamWsHandler.class);
    private static final int MAX_LOG_BUFFER = 200;

    private static final CopyOnWriteArrayList<Channel> channels = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<String> recentLogs = new CopyOnWriteArrayList<>();
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final AtomicLong logCounter = new AtomicLong(0);
    private final MarsLinkerUiProperties uiProperties;

    public LogStreamWsHandler(MarsLinkerUiProperties uiProperties) {
        this.uiProperties = uiProperties;
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
            if (uiProperties.isAuthEnabled()) {
                WebSocketServerProtocolHandler.HandshakeComplete handshake = (WebSocketServerProtocolHandler.HandshakeComplete) evt;
                if (!validateWsToken(handshake.requestUri())) {
                    log.warn("Log stream WS auth failed, closing: {}", ctx.channel().id());
                    ctx.close();
                    return;
                }
            }
            channels.add(ctx.channel());
            for (String entry : recentLogs) {
                ctx.channel().writeAndFlush(new TextWebSocketFrame(entry));
            }
            log.debug("Log stream WS connected: {}, total: {}", ctx.channel().id(), channels.size());
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame msg) {
        // client messages ignored
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        channels.remove(ctx.channel());
        log.debug("Log stream WS disconnected: {}, total: {}", ctx.channel().id(), channels.size());
        super.channelInactive(ctx);
    }

    public static void pushLog(String level, String loggerName, String message, String threadName) {
        try {
            LogEntry entry = new LogEntry(
                    logCounter.incrementAndGet(),
                    System.currentTimeMillis(),
                    level, loggerName, message, threadName
            );
            String json = objectMapper.writeValueAsString(entry);
            recentLogs.add(json);
            if (recentLogs.size() > MAX_LOG_BUFFER) {
                recentLogs.remove(0);
            }
            TextWebSocketFrame frame = new TextWebSocketFrame(json);
            for (Channel ch : channels) {
                if (ch.isActive()) {
                    ch.writeAndFlush(frame.retain());
                } else {
                    channels.remove(ch);
                }
            }
            frame.release();
        } catch (Exception e) {
            log.warn("Failed to push log entry via WS: {}", e.getMessage());
        }
    }

    public static int activeChannelCount() {
        return channels.size();
    }

    private boolean validateWsToken(String requestUri) {
        try {
            QueryStringDecoder qs = new QueryStringDecoder(requestUri);
            List<String> tokenParams = qs.parameters().get("token");
            if (tokenParams == null || tokenParams.isEmpty()) {
                return false;
            }
            String token = tokenParams.get(0);
            SignedJWT jwt = SignedJWT.parse(token);
            com.nimbusds.jose.crypto.MACVerifier verifier =
                    new com.nimbusds.jose.crypto.MACVerifier(
                            uiProperties.getJwtSecretKey().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            if (!jwt.verify(verifier)) {
                return false;
            }
            java.util.Date exp = jwt.getJWTClaimsSet().getExpirationTime();
            return exp != null && exp.after(new java.util.Date());
        } catch (Exception e) {
            log.debug("WS token validation error: {}", e.getMessage());
            return false;
        }
    }

    public static class LogEntry {
        public long id;
        public long timestamp;
        public String level;
        public String logger;
        public String message;
        public String thread;

        public LogEntry(long id, long timestamp, String level, String logger, String message, String thread) {
            this.id = id;
            this.timestamp = timestamp;
            this.level = level;
            this.logger = logger;
            this.message = message;
            this.thread = thread;
        }
    }
}
