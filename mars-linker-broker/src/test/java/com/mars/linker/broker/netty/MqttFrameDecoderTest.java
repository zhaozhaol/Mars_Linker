
package com.mars.linker.broker.netty;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
/**
 * {@link MqttFrameDecoder} 单元测试（EmbeddedChannel）。
 * <p>
 * <b>覆盖</b>：单帧 PINGREQ、半包等待、非法类型关闭。
 * </p>
 * <p>
 * <b>建议补充（与类 JavaDoc 清单一致）</b>：Remaining Length 多字节、整帧超过 maxPacketBytes、粘多帧、非法 RL 编码。
 * </p>
 *//*

class MqttFrameDecoderTest {

    @Test
    void pingReqSingleFrame() {
        EmbeddedChannel ch = new EmbeddedChannel(new MqttFrameDecoder(1024));
        ch.writeInbound(Unpooled.wrappedBuffer(new byte[]{(byte) 0xC0, 0x00}));
        ByteBuf frame = ch.readInbound();
        assertNotNull(frame);
        assertTrue(frame.isReadable(2));
        frame.release();
        assertNull(ch.readInbound());
        assertFalse(ch.finish());
    }

    @Test
    void incompleteRemainingLengthWaits() {
        EmbeddedChannel ch = new EmbeddedChannel(new MqttFrameDecoder(1024));
        ch.writeInbound(Unpooled.wrappedBuffer(new byte[]{(byte) 0xC0}));
        assertNull(ch.readInbound());
        ch.writeInbound(Unpooled.wrappedBuffer(new byte[]{0x00}));
        ByteBuf frame = ch.readInbound();
        assertNotNull(frame);
        frame.release();
        assertFalse(ch.finish());
    }

    @Test
    void illegalMessageTypeClosesChannel() {
        EmbeddedChannel ch = new EmbeddedChannel(new MqttFrameDecoder(1024));
        ch.writeInbound(Unpooled.wrappedBuffer(new byte[]{(byte) 0xF0, 0x00}));
        assertFalse(ch.isOpen());
    }

    @Test
    void multiByteRemainingLengthFrameShouldDecode() {
        EmbeddedChannel ch = new EmbeddedChannel(new MqttFrameDecoder(1024));
        byte[] payload = new byte[130];
        Arrays.fill(payload, (byte) 0x11);
        ByteBuf buf = Unpooled.buffer(1 + 2 + payload.length);
        buf.writeByte(0x30);
        buf.writeByte(0x82); // RL first byte (130)
        buf.writeByte(0x01); // RL second byte
        buf.writeBytes(payload);

        ch.writeInbound(buf);
        ByteBuf frame = ch.readInbound();
        assertNotNull(frame);
        assertTrue(frame.readableBytes() == 133);
        frame.release();
        assertFalse(ch.finish());
    }

    @Test
    void frameLargerThanMaxPacketBytesShouldCloseChannel() {
        EmbeddedChannel ch = new EmbeddedChannel(new MqttFrameDecoder(10));
        byte[] payload = new byte[16];
        ByteBuf buf = Unpooled.buffer(1 + 1 + payload.length);
        buf.writeByte(0x30);
        buf.writeByte(payload.length);
        buf.writeBytes(payload);
        ch.writeInbound(buf);
        assertFalse(ch.isOpen());
    }

    @Test
    void invalidRemainingLengthEncodingShouldCloseChannel() {
        EmbeddedChannel ch = new EmbeddedChannel(new MqttFrameDecoder(1024));
        ch.writeInbound(Unpooled.wrappedBuffer(new byte[]{0x30, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, 0x00}));
        assertFalse(ch.isOpen());
    }
}
*/
class MqttFrameDecoderTest {

    public static void main(String[] args) throws MqttException {
        for (int i = 0; i < 10000; i++) {
            MqttClient client = new MqttClient("tcp://140.210.218.252:80", "bench-" + i);
            client.connect(new MqttConnectOptions());
        }
    }

}