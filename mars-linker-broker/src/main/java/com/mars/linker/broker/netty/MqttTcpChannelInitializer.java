package com.mars.linker.broker.netty;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;

/**
 * MQTT TCP 子通道管道：{@link LoggingHandler}（INFO）→ {@link MqttFrameDecoder} → {@link MqttProtocolHandler}。
 * <p>
 * <b>测试备注</b>：管道级行为（半包/粘包、真实客户端）建议用集成测试或本地连 {@link NettyMqttBrokerServer}；
 * 解码与协议逻辑以各 Handler 的单元测试为主。
 * </p>
 */
public class MqttTcpChannelInitializer extends ChannelInitializer<SocketChannel> {

    private final int maxPacketBytes;
    private final MqttProtocolHandler mqttProtocolHandler;
    private final SslContext sslContext;

    public MqttTcpChannelInitializer(int maxPacketBytes, MqttProtocolHandler mqttProtocolHandler) {
        this(maxPacketBytes, mqttProtocolHandler, null);
    }

    public MqttTcpChannelInitializer(int maxPacketBytes, MqttProtocolHandler mqttProtocolHandler, SslContext sslContext) {
        this.maxPacketBytes = maxPacketBytes;
        this.mqttProtocolHandler = mqttProtocolHandler;
        this.sslContext = sslContext;
    }

    @Override
    protected void initChannel(SocketChannel ch) {
        if (sslContext != null) {
            ch.pipeline().addLast("mqttSsl", sslContext.newHandler(ch.alloc()));
        }
        ch.pipeline().addLast(new LoggingHandler(LogLevel.INFO));
        ch.pipeline().addLast("mqttFrameDecoder", new MqttFrameDecoder(maxPacketBytes));
        ch.pipeline().addLast("mqttProtocolHandler", mqttProtocolHandler);
    }
}
