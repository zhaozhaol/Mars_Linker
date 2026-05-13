package com.mars.linker.broker.netty;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * MQTT 3.1.1 <b>帧切分</b>（{@link ByteToMessageDecoder}，基于 Remaining Length 的变长头）。
 * <p>
 * 从 TCP 字节流中解析：固定头第 1 字节（消息类型）+ Remaining Length（变长 1～4 字节）+ 剩余负载，
 * 输出<b>一整帧</b>的 {@link ByteBuf}（retained slice），交给 {@link MqttProtocolHandler} 做语义解析。
 * </p>
 * <p>
 * <b>不负责</b>：CONNECT/PUBLISH 内容语义校验；仅做最小结构校验与帧边界提取。
 * 当检测到明显非法（消息类型、Remaining Length 编码、单帧超限）时，采取<b>关闭连接</b>的失败策略。
 * </p>
 * <p>
 * <b>测试备注</b>（与 {@link MqttFrameDecoderTest} 对应，并建议补充）：
 * </p>
 * <ul>
 *   <li>单帧、半包、粘包、非法类型 0xF0</li>
 *   <li>Remaining Length 多字节编码（&gt;127）</li>
 *   <li>整帧长度超过 {@code maxPacketBytes}（应关闭连接）</li>
 *   <li>超大 Remaining Length 非法编码</li>
 * </ul>
 */
public class MqttFrameDecoder extends ByteToMessageDecoder {

    private static final Logger log = LoggerFactory.getLogger(MqttFrameDecoder.class);

    /** MQTT 规范 Remaining Length 数值上限（28 bit）。 */
    private static final int MQTT_MAX_REMAINING_LENGTH = 268_435_455;

    /** 单帧最大字节数（含固定头 + RL 编码 + 负载），防止 OOM；与 Broker 配置一致。 */
    private final int maxPacketBytes;

    public MqttFrameDecoder(int maxPacketBytes) {
        this.maxPacketBytes = maxPacketBytes;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        final int mark = in.readerIndex();
        if (in.readableBytes() < 1) {
            return;
        }
        byte b0 = in.readByte();
        int type = (b0 >> 4) & 0x0F;
        if (type < 1 || type > 14) {
            in.readerIndex(mark);
            log.warn("MQTT 非法消息类型 0x{}，关闭连接 remote={}",
                    Integer.toHexString(type), ctx.channel().remoteAddress());
            ctx.close();
            return;
        }

        long remainingLength = 0;
        int multiplier = 1;
        int rlDigits = 0;
        while (true) {
            if (!in.isReadable()) {
                in.readerIndex(mark);
                return;
            }
            if (rlDigits >= 4) {
                in.readerIndex(mark);
                log.warn("MQTT Remaining Length 超过 4 字节，关闭连接 remote={}", ctx.channel().remoteAddress());
                ctx.close();
                return;
            }
            int digit = in.readUnsignedByte();
            rlDigits++;
            remainingLength += (long) (digit & 0x7F) * multiplier;
            if (remainingLength > MQTT_MAX_REMAINING_LENGTH) {
                in.readerIndex(mark);
                log.warn("MQTT Remaining Length 超限，关闭连接 remote={}", ctx.channel().remoteAddress());
                ctx.close();
                return;
            }
            if ((digit & 0x80) == 0) {
                break;
            }
            multiplier <<= 7;
        }

        int headerSize = in.readerIndex() - mark;
        long totalFrame = (long) headerSize + remainingLength;
        if (totalFrame > maxPacketBytes || totalFrame > Integer.MAX_VALUE) {
            in.readerIndex(mark);
            log.warn("MQTT 单帧过大 totalFrame={} maxPacketBytes={}，关闭连接 remote={}",
                    totalFrame, maxPacketBytes, ctx.channel().remoteAddress());
            ctx.close();
            return;
        }
        if (in.readableBytes() < remainingLength) {
            in.readerIndex(mark);
            return;
        }

        in.readerIndex(mark);
        out.add(in.readRetainedSlice((int) totalFrame));
        if (log.isTraceEnabled()) {
            log.trace("解码 MQTT 帧 type={} totalBytes={} remote={}",
                    type, totalFrame, ctx.channel().remoteAddress());
        }
    }
}
