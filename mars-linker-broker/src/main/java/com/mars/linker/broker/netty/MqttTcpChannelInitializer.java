package com.mars.linker.broker.netty;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;

/**
 * MQTT TCP 子通道管道：{@link LoggingHandler} → {@link MqttFrameDecoder} → {@link MqttProtocolHandler}。
 * <p>
 * 设计决策：
 * - LoggingHandler 日志级别可通过 pipelineLogLevel 参数配置（默认 DEBUG，生产环境不输出每个报文）
 * - INFO 级别日志仍通过 StructuredLogger 记录连接/断开/鉴权失败等关键事件
 * </p>
 */
public class MqttTcpChannelInitializer extends ChannelInitializer<SocketChannel> {

    private final int maxPacketBytes;
    private final MqttProtocolHandler mqttProtocolHandler;
    private final SslContext sslContext;
    private final LogLevel pipelineLogLevel;

    public MqttTcpChannelInitializer(int maxPacketBytes, MqttProtocolHandler mqttProtocolHandler) {
        this(maxPacketBytes, mqttProtocolHandler, null, LogLevel.DEBUG);
    }

    public MqttTcpChannelInitializer(int maxPacketBytes, MqttProtocolHandler mqttProtocolHandler, SslContext sslContext) {
        this(maxPacketBytes, mqttProtocolHandler, sslContext, LogLevel.DEBUG);
    }

    public MqttTcpChannelInitializer(int maxPacketBytes, MqttProtocolHandler mqttProtocolHandler,
                                     SslContext sslContext, LogLevel pipelineLogLevel) {
        this.maxPacketBytes = maxPacketBytes;
        this.mqttProtocolHandler = mqttProtocolHandler;
        this.sslContext = sslContext;
        this.pipelineLogLevel = pipelineLogLevel != null ? pipelineLogLevel : LogLevel.DEBUG;
    }

    @Override
    protected void initChannel(SocketChannel ch) {
        if (sslContext != null) {
            ch.pipeline().addLast("mqttSsl", sslContext.newHandler(ch.alloc()));
        }
        ch.pipeline().addLast(new LoggingHandler(pipelineLogLevel));
        ch.pipeline().addLast("mqttFrameDecoder", new MqttFrameDecoder(maxPacketBytes));
        ch.pipeline().addLast("mqttProtocolHandler", mqttProtocolHandler);
    }
}
