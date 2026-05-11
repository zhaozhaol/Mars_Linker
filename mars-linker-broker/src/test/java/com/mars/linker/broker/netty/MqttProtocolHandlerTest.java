//package com.mars.linker.broker.netty;
//
//import io.netty.buffer.ByteBuf;
//import io.netty.buffer.Unpooled;
//import io.netty.channel.embedded.EmbeddedChannel;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//
//import java.io.IOException;
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.nio.charset.StandardCharsets;
//import java.util.Arrays;
//
//import static org.junit.jupiter.api.Assertions.assertEquals;
//import static org.junit.jupiter.api.Assertions.assertFalse;
//import static org.junit.jupiter.api.Assertions.assertNotNull;
//import static org.junit.jupiter.api.Assertions.assertNull;
//import static org.junit.jupiter.api.Assertions.assertTrue;
//
///**
// * {@link MqttProtocolHandler} 单元测试（EmbeddedChannel，不经由 {@link MqttFrameDecoder}）。
// * <p>
// * <b>覆盖</b>：CONNECT 3.1.1 / 拒绝非 3.1.1、PING、精确主题订阅+发布、QoS1 投递与 PUBACK、DISCONNECT 等。
// * </p>
// * <p>
// * <b>建议补充</b>：
// * </p>
// * <ul>
// *   <li>协议违规与边界：未 CONNECT 收到其它报文、Remaining Length/长度不一致、SUBSCRIBE flags 非法等负例</li>
// *   <li>发布自收：发布者同主题已订阅时是否收到自身（当前实现会收到，后续与 EMQX 行为对齐再定）</li>
// *   <li>下行 QoS1：重传与 DUP 标志（实现重传后补测）</li>
// *   <li>通配符与 {@code $share}（实现后）</li>
// *   <li>Will/Retain/QoS2（实现后）</li>
// *   <li>鉴权失败路径（实现后）</li>
// * </ul>
// * </p>
// */
//class MqttProtocolHandlerTest {
//    private static final Path SESSION_STORE_PATH = Path.of("data", "session-store.tsv");
//
//    @BeforeEach
//    void resetState() {
//        MqttProtocolHandler.resetStateForTests();
//    }
//
//    @Test
//    void connect311ShouldReceiveConnAckAccepted() {
//        EmbeddedChannel ch = new EmbeddedChannel(new MqttProtocolHandler());
//        ch.writeInbound(connectPacket("clientA", 0x04));
//
//        ByteBuf out = ch.readOutbound();
//        assertNotNull(out);
//        assertEquals(0x20, out.readUnsignedByte());
//        assertEquals(0x02, out.readUnsignedByte());
//        assertEquals(0x00, out.readUnsignedByte());
//        assertEquals(0x00, out.readUnsignedByte());
//        out.release();
//        assertTrue(ch.isOpen());
//        assertFalse(ch.finish());
//    }
//
//    @Test
//    void connect50ShouldReceiveConnAckAcceptedWithPropertiesLength() {
//        EmbeddedChannel ch = new EmbeddedChannel(new MqttProtocolHandler());
//        ch.writeInbound(connectPacket5("client5"));
//
//        ByteBuf out = ch.readOutbound();
//        assertNotNull(out);
//        assertEquals(0x20, out.readUnsignedByte());
//        assertEquals(0x03, out.readUnsignedByte()); // remaining length (ackFlags + reason + propsLen)
//        assertEquals(0x00, out.readUnsignedByte()); // ack flags
//        assertEquals(0x00, out.readUnsignedByte()); // reason code success
//        assertEquals(0x00, out.readUnsignedByte()); // properties length
//        out.release();
//        assertTrue(ch.isOpen());
//        assertFalse(ch.finish());
//    }
//
//    @Test
//    void mqtt5SubscribeAndPublishShouldWorkWithProperties() {
//        MqttProtocolHandler handler = new MqttProtocolHandler();
//        EmbeddedChannel sub = new EmbeddedChannel(handler);
//        EmbeddedChannel pub = new EmbeddedChannel(handler);
//
//        sub.writeInbound(connectPacket5("sub5"));
//        sub.readOutbound().release(); // connack
//        pub.writeInbound(connectPacket5("pub5"));
//        pub.readOutbound().release(); // connack
//
//        sub.writeInbound(subscribePacket5(1, "t/5", 0));
//        ByteBuf subAck = sub.readOutbound();
//        assertNotNull(subAck);
//        assertEquals(0x90, subAck.readUnsignedByte());
//        // mqtt5 suback includes properties length byte
//        subAck.release();
//
//        pub.writeInbound(publishPacket5("t/5", new byte[]{0x01, 0x02}));
//        ByteBuf delivered = sub.readOutbound();
//        assertNotNull(delivered);
//        // 固定头
//        assertEquals(0x30, delivered.readUnsignedByte());
//        // Remaining Length（可能为单字节，这里仅验证后续结构包含 properties length=0）
//        delivered.readUnsignedByte();
//        int tl = delivered.readUnsignedShort();
//        delivered.skipBytes(tl);
//        int propsLen = delivered.readUnsignedByte();
//        assertEquals(0x00, propsLen);
//        delivered.release();
//
//        assertFalse(sub.finish());
//        assertFalse(pub.finish());
//    }
//
//    @Test
//    void connectWithAuthEnabledAndCorrectCredentialsShouldBeAccepted() {
//        EmbeddedChannel ch = new EmbeddedChannel(new MqttProtocolHandler(true, "u1", "p1"));
//        ch.writeInbound(connectPacketWithAuth("client-auth-ok", 0x04, "u1", "p1"));
//
//        ByteBuf out = ch.readOutbound();
//        assertNotNull(out);
//        assertEquals(0x20, out.readUnsignedByte());
//        assertEquals(0x02, out.readUnsignedByte());
//        assertEquals(0x00, out.readUnsignedByte());
//        assertEquals(0x00, out.readUnsignedByte());
//        out.release();
//        assertTrue(ch.isOpen());
//        assertFalse(ch.finish());
//    }
//
//    @Test
//    void connectWithAuthEnabledAndWrongCredentialsShouldBeRejected() {
//        EmbeddedChannel ch = new EmbeddedChannel(new MqttProtocolHandler(true, "u1", "p1"));
//        ch.writeInbound(connectPacketWithAuth("client-auth-bad", 0x04, "u1", "bad"));
//
//        ByteBuf out = ch.readOutbound();
//        assertNotNull(out);
//        assertEquals(0x20, out.readUnsignedByte());
//        assertEquals(0x02, out.readUnsignedByte());
//        assertEquals(0x00, out.readUnsignedByte());
//        assertEquals(0x05, out.readUnsignedByte()); // Not authorized
//        out.release();
//        assertFalse(ch.isOpen());
//    }
//
//    @Test
//    void willShouldBePublishedOnAbnormalDisconnectOnly() {
//        MqttProtocolHandler handler = new MqttProtocolHandler();
//        EmbeddedChannel sub = new EmbeddedChannel(handler);
//        EmbeddedChannel willClient = new EmbeddedChannel(handler);
//
//        sub.writeInbound(connectPacket("sub-will", 0x04));
//        sub.readOutbound().release();
//        sub.writeInbound(subscribePacket(1, "will/t", 0));
//        sub.readOutbound().release();
//
//        willClient.writeInbound(connectPacketWithWill("will-client", 0x04, "will/t", new byte[]{0x41, 0x42}, 0, false));
//        willClient.readOutbound().release();
//
//        // 异常断开：直接 close，不发 DISCONNECT，应触发 Will 投递。
//        willClient.close();
//        ByteBuf out = sub.readOutbound();
//        assertNotNull(out);
//        assertEquals(0x30, out.readUnsignedByte());
//        int rl = out.readUnsignedByte();
//        assertTrue(rl > 0);
//        assertEquals("will/t".length(), out.readUnsignedShort());
//        byte[] topicBytes = new byte["will/t".length()];
//        out.readBytes(topicBytes);
//        assertEquals("will/t", new String(topicBytes, StandardCharsets.UTF_8));
//        byte[] payload = new byte[out.readableBytes()];
//        out.readBytes(payload);
//        assertTrue(Arrays.equals(new byte[]{0x41, 0x42}, payload));
//        out.release();
//
//        // 正常断开：发 DISCONNECT，不应触发 Will。
//        EmbeddedChannel willClient2 = new EmbeddedChannel(handler);
//        willClient2.writeInbound(connectPacketWithWill("will-client2", 0x04, "will/t", new byte[]{0x55}, 0, false));
//        willClient2.readOutbound().release();
//        willClient2.writeInbound(disconnectPacket());
//        assertFalse(willClient2.isOpen());
//        assertNull(sub.readOutbound());
//
//        assertFalse(sub.finish());
//        assertFalse(willClient.finish());
//        assertFalse(willClient2.finish());
//    }
//
//    @Test
//    void connectWithUnsupportedLevelShouldBeRejected() {
//        EmbeddedChannel ch = new EmbeddedChannel(new MqttProtocolHandler());
//        ch.writeInbound(connectPacket("clientA", 0x05));
//
//        ByteBuf out = ch.readOutbound();
//        assertNotNull(out);
//        assertEquals(0x20, out.readUnsignedByte());
//        assertEquals(0x02, out.readUnsignedByte());
//        assertEquals(0x00, out.readUnsignedByte());
//        assertEquals(0x01, out.readUnsignedByte());
//        out.release();
//    }
//
//    @Test
//    void pingReqShouldReceivePingResp() {
//        EmbeddedChannel ch = new EmbeddedChannel(new MqttProtocolHandler());
//        ch.writeInbound(Unpooled.wrappedBuffer(new byte[]{(byte) 0xC0, 0x00}));
//
//        ByteBuf out = ch.readOutbound();
//        assertNotNull(out);
//        assertEquals(0xD0, out.readUnsignedByte());
//        assertEquals(0x00, out.readUnsignedByte());
//        out.release();
//        assertFalse(ch.finish());
//    }
//
//    @Test
//    void subscribeThenPublishShouldFanOutToExactSubscribers() {
//        MqttProtocolHandler handler = new MqttProtocolHandler();
//        EmbeddedChannel sub = new EmbeddedChannel(handler);
//        EmbeddedChannel pub = new EmbeddedChannel(handler);
//
//        sub.writeInbound(connectPacket("sub-client", 0x04));
//        ByteBuf subConnAck = sub.readOutbound();
//        assertNotNull(subConnAck);
//        subConnAck.release();
//
//        pub.writeInbound(connectPacket("pub-client", 0x04));
//        ByteBuf pubConnAck = pub.readOutbound();
//        assertNotNull(pubConnAck);
//        pubConnAck.release();
//
//        sub.writeInbound(subscribePacket(1, "dev/topic", 0));
//        ByteBuf subAck = sub.readOutbound();
//        assertNotNull(subAck);
//        assertEquals(0x90, subAck.readUnsignedByte());
//        assertEquals(0x03, subAck.readUnsignedByte()); // remaining length
//        assertEquals(0x00, subAck.readUnsignedByte()); // packet id high
//        assertEquals(0x01, subAck.readUnsignedByte()); // packet id low
//        assertEquals(0x00, subAck.readUnsignedByte()); // granted qos
//        subAck.release();
//
//        byte[] body = new byte[]{0x11, 0x22, 0x33};
//        pub.writeInbound(publishPacket("dev/topic", body));
//
//        ByteBuf out = sub.readOutbound();
//        assertNotNull(out);
//        assertEquals(0x30, out.readUnsignedByte());
//        assertEquals(2 + "dev/topic".length() + body.length, out.readUnsignedByte());
//        assertEquals("dev/topic".length(), out.readUnsignedShort());
//        byte[] topicBytes = new byte["dev/topic".length()];
//        out.readBytes(topicBytes);
//        assertEquals("dev/topic", new String(topicBytes, StandardCharsets.UTF_8));
//        byte[] payload = new byte[out.readableBytes()];
//        out.readBytes(payload);
//        assertTrue(Arrays.equals(body, payload));
//        out.release();
//
//        assertNull(pub.readOutbound());
//        assertFalse(sub.finish());
//        assertFalse(pub.finish());
//    }
//
//    @Test
//    void wildcardPlusShouldMatchOneLevel() {
//        MqttProtocolHandler handler = new MqttProtocolHandler();
//        EmbeddedChannel sub = new EmbeddedChannel(handler);
//        EmbeddedChannel pub = new EmbeddedChannel(handler);
//
//        sub.writeInbound(connectPacket("sub-plus", 0x04));
//        sub.readOutbound().release();
//        pub.writeInbound(connectPacket("pub-plus", 0x04));
//        pub.readOutbound().release();
//
//        sub.writeInbound(subscribePacket(1, "dev/+/t", 0));
//        sub.readOutbound().release();
//
//        pub.writeInbound(publishPacket("dev/a/t", new byte[]{0x01}));
//        ByteBuf out = sub.readOutbound();
//        assertNotNull(out);
//        assertEquals(0x30, out.readUnsignedByte());
//        out.release();
//
//        pub.writeInbound(publishPacket("dev/a/b/t", new byte[]{0x02}));
//        assertNull(sub.readOutbound());
//
//        assertFalse(sub.finish());
//        assertFalse(pub.finish());
//    }
//
//    @Test
//    void wildcardHashShouldMatchMultipleLevels() {
//        MqttProtocolHandler handler = new MqttProtocolHandler();
//        EmbeddedChannel sub = new EmbeddedChannel(handler);
//        EmbeddedChannel pub = new EmbeddedChannel(handler);
//
//        sub.writeInbound(connectPacket("sub-hash", 0x04));
//        sub.readOutbound().release();
//        pub.writeInbound(connectPacket("pub-hash", 0x04));
//        pub.readOutbound().release();
//
//        sub.writeInbound(subscribePacket(1, "dev/#", 0));
//        sub.readOutbound().release();
//
//        pub.writeInbound(publishPacket("dev/a/b", new byte[]{0x11}));
//        assertNotNull(sub.readOutbound());
//
//        pub.writeInbound(publishPacket("x/dev/a", new byte[]{0x22}));
//        assertNull(sub.readOutbound());
//
//        assertFalse(sub.finish());
//        assertFalse(pub.finish());
//    }
//
//    @Test
//    void shareSubscriptionShouldDeliverToSingleSubscriberPerPublish() {
//        MqttProtocolHandler handler = new MqttProtocolHandler();
//        EmbeddedChannel s1 = new EmbeddedChannel(handler);
//        EmbeddedChannel s2 = new EmbeddedChannel(handler);
//        EmbeddedChannel pub = new EmbeddedChannel(handler);
//
//        s1.writeInbound(connectPacket("s1", 0x04));
//        s1.readOutbound().release();
//        s2.writeInbound(connectPacket("s2", 0x04));
//        s2.readOutbound().release();
//        pub.writeInbound(connectPacket("pub-share", 0x04));
//        pub.readOutbound().release();
//
//        s1.writeInbound(subscribePacket(1, "$share/g1/dev/+/t", 0));
//        s1.readOutbound().release();
//        s2.writeInbound(subscribePacket(1, "$share/g1/dev/+/t", 0));
//        s2.readOutbound().release();
//
//        pub.writeInbound(publishPacket("dev/a/t", new byte[]{0x7F}));
//
//        ByteBuf o1 = s1.readOutbound();
//        ByteBuf o2 = s2.readOutbound();
//        // 同一个 publish，只能投递到一个共享订阅者
//        assertTrue((o1 == null) ^ (o2 == null));
//        if (o1 != null) {
//            o1.release();
//        }
//        if (o2 != null) {
//            o2.release();
//        }
//        assertFalse(s1.finish());
//        assertFalse(s2.finish());
//        assertFalse(pub.finish());
//    }
//
//    @Test
//    void shareExactTopicShouldDeliverOnlyOnceForConfiguredDeviceLogicPattern() {
//        MqttProtocolHandler handler = new MqttProtocolHandler();
//        EmbeddedChannel s1 = new EmbeddedChannel(handler);
//        EmbeddedChannel s2 = new EmbeddedChannel(handler);
//        EmbeddedChannel pub = new EmbeddedChannel(handler);
//
//        s1.writeInbound(connectPacket("dl-s1", 0x04));
//        s1.readOutbound().release();
//        s2.writeInbound(connectPacket("dl-s2", 0x04));
//        s2.readOutbound().release();
//        pub.writeInbound(connectPacket("dl-pub", 0x04));
//        pub.readOutbound().release();
//
//        // 对齐现网 device_logic 配置：$share/devGroup/devices/connected
//        String shareFilter = "$share/devGroup/devices/connected";
//        s1.writeInbound(subscribePacket(11, shareFilter, 1));
//        ByteBuf subAck1 = s1.readOutbound();
//        assertNotNull(subAck1);
//        subAck1.release();
//        s2.writeInbound(subscribePacket(12, shareFilter, 1));
//        ByteBuf subAck2 = s2.readOutbound();
//        assertNotNull(subAck2);
//        subAck2.release();
//
//        pub.writeInbound(publishPacket("devices/connected", new byte[]{0x01}));
//        ByteBuf pubAck = pub.readOutbound();
//        assertNotNull(pubAck);
//        pubAck.release();
//
//        ByteBuf out1 = s1.readOutbound();
//        ByteBuf out2 = s2.readOutbound();
//        // 共享组内单条消息只投递一个订阅者
//        assertTrue((out1 == null) ^ (out2 == null));
//        if (out1 != null) {
//            out1.release();
//        }
//        if (out2 != null) {
//            out2.release();
//        }
//
//        assertFalse(s1.finish());
//        assertFalse(s2.finish());
//        assertFalse(pub.finish());
//    }
//
//    @Test
//    void deviceConnectedEventShouldBePublishedForSharedSubscriberWithPort() {
//        MqttProtocolHandler handler = new MqttProtocolHandler();
//        EmbeddedChannel dg = new EmbeddedChannel(handler);
//        EmbeddedChannel device = new EmbeddedChannel(handler);
//
//        dg.writeInbound(connectPacket("dg-1", 0x04));
//        dg.readOutbound().release();
//        dg.writeInbound(subscribePacket(1, "$share/devGroup/devices/connected", 1));
//        dg.readOutbound().release();
//
//        device.writeInbound(connectPacket("device_862419079236500", 0x04));
//        ByteBuf connAck = device.readOutbound();
//        assertNotNull(connAck);
//        connAck.release();
//
//        ByteMsg event = readPublishFromSub(dg);
//        assertEquals("devices/connected", event.topic);
//        String json = new String(event.payload, StandardCharsets.UTF_8);
//        assertTrue(json.contains("\"event\":\"connected\""));
//        assertTrue(json.contains("\"clientId\":\"device_862419079236500\""));
//        assertTrue(json.contains("\"port\":"));
//        assertTrue(json.contains("\"reason\":\"connect\""));
//
//        assertFalse(dg.finish());
//        assertFalse(device.finish());
//    }
//
//    @Test
//    void deviceOfflineEventShouldBePublishedForSharedSubscriberWithPort() {
//        MqttProtocolHandler handler = new MqttProtocolHandler();
//        EmbeddedChannel dg = new EmbeddedChannel(handler);
//        EmbeddedChannel device = new EmbeddedChannel(handler);
//
//        dg.writeInbound(connectPacket("dg-2", 0x04));
//        dg.readOutbound().release();
//        dg.writeInbound(subscribePacket(1, "$share/devGroup/devices/offline", 1));
//        dg.readOutbound().release();
//
//        device.writeInbound(connectPacket("device_862419079236500", 0x04));
//        ByteBuf connAck = device.readOutbound();
//        assertNotNull(connAck);
//        connAck.release();
//
//        // 吃掉 connected 事件（若无订阅 connected 不会有消息）
//        assertNull(dg.readOutbound());
//
//        device.writeInbound(disconnectPacket());
//        assertFalse(device.isOpen());
//
//        ByteMsg event = readPublishFromSub(dg);
//        assertEquals("devices/offline", event.topic);
//        String json = new String(event.payload, StandardCharsets.UTF_8);
//        assertTrue(json.contains("\"event\":\"offline\""));
//        assertTrue(json.contains("\"clientId\":\"device_862419079236500\""));
//        assertTrue(json.contains("\"port\":"));
//        assertTrue(json.contains("\"reason\":\"graceful_disconnect\""));
//
//        assertFalse(dg.finish());
//        assertFalse(device.finish());
//    }
//
//    @Test
//    void delayedPublishShouldDeliverAfterConfiguredSeconds() {
//        MqttProtocolHandler handler = new MqttProtocolHandler();
//        EmbeddedChannel sub = new EmbeddedChannel(handler);
//        EmbeddedChannel pub = new EmbeddedChannel(handler);
//
//        sub.writeInbound(connectPacket("sub-delayed", 0x04));
//        sub.readOutbound().release();
//        pub.writeInbound(connectPacket("pub-delayed", 0x04));
//        pub.readOutbound().release();
//
//        sub.writeInbound(subscribePacket(1, "devices/connected", 0));
//        ByteBuf subAck = sub.readOutbound();
//        assertNotNull(subAck);
//        subAck.release();
//
//        pub.writeInbound(publishPacket("$delayed/1/devices/connected", new byte[]{0x5A}));
//        // 立刻不应投递
//        assertNull(sub.readOutbound());
//
//        // 推进时间后再触发投递
//        sub.runScheduledPendingTasks();
//        sub.advanceTimeBy(1200, java.util.concurrent.TimeUnit.MILLISECONDS);
//        sub.runScheduledPendingTasks();
//
//        ByteBuf delayedOut = sub.readOutbound();
//        assertNotNull(delayedOut);
//        assertEquals(0x30, delayedOut.readUnsignedByte());
//        delayedOut.release();
//
//        assertFalse(sub.finish());
//        assertFalse(pub.finish());
//    }
//
//    @Test
//    void invalidDelayedTopicShouldClosePublisher() {
//        MqttProtocolHandler handler = new MqttProtocolHandler();
//        EmbeddedChannel pub = new EmbeddedChannel(handler);
//        pub.writeInbound(connectPacket("pub-delayed-bad", 0x04));
//        pub.readOutbound().release();
//
//        // 非法：缺少目标 topic
//        pub.writeInbound(publishPacket("$delayed/10", new byte[]{0x01}));
//        assertFalse(pub.isOpen());
//    }
//
//    @Test
//    void unsubscribeShouldStopDelivery() {
//        MqttProtocolHandler handler = new MqttProtocolHandler();
//        EmbeddedChannel sub = new EmbeddedChannel(handler);
//        EmbeddedChannel pub = new EmbeddedChannel(handler);
//
//        sub.writeInbound(connectPacket("sub-unsub", 0x04));
//        sub.readOutbound().release();
//        pub.writeInbound(connectPacket("pub-unsub", 0x04));
//        pub.readOutbound().release();
//
//        sub.writeInbound(subscribePacket(1, "dev/+/t", 0));
//        sub.readOutbound().release();
//
//        pub.writeInbound(publishPacket("dev/a/t", new byte[]{0x01}));
//        ByteBuf out1 = sub.readOutbound();
//        assertNotNull(out1);
//        out1.release();
//
//        sub.writeInbound(unsubscribePacket(7, "dev/+/t"));
//        ByteBuf unSubAck = sub.readOutbound();
//        assertNotNull(unSubAck);
//        assertEquals(0xB0, unSubAck.readUnsignedByte());
//        assertEquals(0x02, unSubAck.readUnsignedByte());
//        assertEquals(7, unSubAck.readUnsignedShort());
//        unSubAck.release();
//
//        pub.writeInbound(publishPacket("dev/a/t", new byte[]{0x02}));
//        assertNull(sub.readOutbound());
//
//        assertFalse(sub.finish());
//        assertFalse(pub.finish());
//    }
//
//    @Test
//    void publishQoS1ShouldReceivePubAck() {
//        EmbeddedChannel ch = new EmbeddedChannel(new MqttProtocolHandler());
//        ch.writeInbound(connectPacket("pub-qos1", 0x04));
//        assertNotNull(ch.readOutbound());
//
//        ByteBuf pub = publishPacketQoS1("t/a", new byte[]{0x01}, 77);
//        ch.writeInbound(pub);
//
//        ByteBuf ack = ch.readOutbound();
//        assertNotNull(ack);
//        assertEquals(0x40, ack.readUnsignedByte());
//        assertEquals(0x02, ack.readUnsignedByte());
//        assertEquals(77, ack.readUnsignedShort());
//        ack.release();
//        assertFalse(ch.finish());
//    }
//
//    @Test
//    void subscribeQoS1AndPublishQoS1DeliversWithPacketId() {
//        MqttProtocolHandler handler = new MqttProtocolHandler();
//        EmbeddedChannel sub = new EmbeddedChannel(handler);
//        EmbeddedChannel pub = new EmbeddedChannel(handler);
//
//        sub.writeInbound(connectPacket("sub-q1", 0x04));
//        sub.readOutbound().release();
//        pub.writeInbound(connectPacket("pub-q1", 0x04));
//        pub.readOutbound().release();
//
//        sub.writeInbound(subscribePacket(1, "q/t", 1));
//        sub.readOutbound().release();
//
//        pub.writeInbound(publishPacketQoS1("q/t", new byte[]{0x55}, 99));
//        ByteBuf pubAck = pub.readOutbound();
//        assertNotNull(pubAck);
//        assertEquals(0x40, pubAck.readUnsignedByte());
//        pubAck.skipBytes(1);
//        assertEquals(99, pubAck.readUnsignedShort());
//        pubAck.release();
//
//        ByteMsg out = readPublishFromSub(sub);
//        assertEquals(3, out.fixedHeader >> 4);
//        assertEquals(0x32, out.fixedHeader & 0xFF);
//        assertEquals("q/t", out.topic);
//        assertEquals(1, out.payload.length);
//        assertEquals(0x55, out.payload[0] & 0xFF);
//        assertTrue(out.packetId > 0);
//        assertFalse(sub.finish());
//        assertFalse(pub.finish());
//    }
//
//    @Test
//    void outboundQoS1ShouldRetransmitWithDupUntilPubAck() {
//        // interval=10ms, maxAttempts=2
//        MqttProtocolHandler handler = new MqttProtocolHandler(false, null, null, true, 10, 2, false, null, null);
//        EmbeddedChannel sub = new EmbeddedChannel(handler);
//        EmbeddedChannel pub = new EmbeddedChannel(handler);
//
//        sub.writeInbound(connectPacket("sub-r", 0x04));
//        sub.readOutbound().release();
//        pub.writeInbound(connectPacket("pub-r", 0x04));
//        pub.readOutbound().release();
//
//        sub.writeInbound(subscribePacket(1, "r/t", 1));
//        sub.readOutbound().release();
//
//        pub.writeInbound(publishPacketQoS1("r/t", new byte[]{0x01}, 321));
//        pub.readOutbound().release(); // puback
//
//        ByteMsg first = readPublishFromSub(sub);
//        assertEquals(0x32, first.fixedHeader & 0xFF);
//        int pid = first.packetId;
//        assertTrue(pid > 0);
//
//        // 推进时间，触发重传（DUP=1）
//        sub.runScheduledPendingTasks();
//        sub.advanceTimeBy(20, java.util.concurrent.TimeUnit.MILLISECONDS);
//        sub.runScheduledPendingTasks();
//
//        ByteMsg second = readPublishFromSub(sub);
//        assertEquals(0x3A, second.fixedHeader & 0xFF); // DUP=1 + QoS1
//        assertEquals(pid, second.packetId);
//
//        // 发送 PUBACK 后，不应再重传
//        sub.writeInbound(pubAckPacket(pid));
//        sub.runScheduledPendingTasks();
//        sub.advanceTimeBy(50, java.util.concurrent.TimeUnit.MILLISECONDS);
//        sub.runScheduledPendingTasks();
//        assertNull(sub.readOutbound());
//
//        assertFalse(sub.finish());
//        assertFalse(pub.finish());
//    }
//
//    @Test
//    void aclShouldRejectSubscribeAndPublish() {
//        // allow subscribe only for s/, allow publish only for p/
//        MqttProtocolHandler handler = new MqttProtocolHandler(
//                false, null, null,
//                false, 5_000, 3,
//                true,
//                java.util.List.of("s/"),
//                java.util.List.of("p/")
//        );
//        EmbeddedChannel sub = new EmbeddedChannel(handler);
//        EmbeddedChannel pub = new EmbeddedChannel(handler);
//
//        sub.writeInbound(connectPacket("sub-acl", 0x04));
//        sub.readOutbound().release();
//        pub.writeInbound(connectPacket("pub-acl", 0x04));
//        pub.readOutbound().release();
//
//        // subscribe denied
//        sub.writeInbound(subscribePacket(1, "x/t", 0));
//        ByteBuf subAckDenied = sub.readOutbound();
//        assertNotNull(subAckDenied);
//        assertEquals(0x90, subAckDenied.readUnsignedByte());
//        subAckDenied.skipBytes(1); // remaining length
//        subAckDenied.skipBytes(2); // packet id
//        assertEquals(0x80, subAckDenied.readUnsignedByte()); // failure
//        subAckDenied.release();
//
//        // publish denied -> close
//        pub.writeInbound(publishPacket("x/t", new byte[]{0x01}));
//        assertFalse(pub.isOpen());
//
//        assertFalse(sub.finish());
//        assertFalse(pub.finish());
//    }
//
//    @Test
//    void aclDefaultDenyShouldBlockWhenAllowListEmpty() {
//        MqttProtocolHandler handler = new MqttProtocolHandler(
//                false, null, null,
//                false, 5_000, 3,
//                true,
//                java.util.Collections.emptyList(),
//                java.util.Collections.emptyList(),
//                true,
//                java.util.Collections.emptyList(),
//                java.util.Collections.emptyList()
//        );
//        EmbeddedChannel sub = new EmbeddedChannel(handler);
//        EmbeddedChannel pub = new EmbeddedChannel(handler);
//
//        sub.writeInbound(connectPacket("sub-acl-dd", 0x04));
//        sub.readOutbound().release();
//        pub.writeInbound(connectPacket("pub-acl-dd", 0x04));
//        pub.readOutbound().release();
//
//        sub.writeInbound(subscribePacket(1, "dev/t", 0));
//        ByteBuf subAckDenied = sub.readOutbound();
//        assertNotNull(subAckDenied);
//        assertEquals(0x90, subAckDenied.readUnsignedByte());
//        subAckDenied.skipBytes(1);
//        subAckDenied.skipBytes(2);
//        assertEquals(0x80, subAckDenied.readUnsignedByte());
//        subAckDenied.release();
//
//        pub.writeInbound(publishPacket("dev/t", new byte[]{0x01}));
//        assertFalse(pub.isOpen());
//
//        assertTrue(MqttProtocolHandler.metricAclSubscribeDenyTotal() >= 1);
//        assertTrue(MqttProtocolHandler.metricAclPublishDenyTotal() >= 1);
//        assertFalse(sub.finish());
//        assertFalse(pub.finish());
//    }
//
//    @Test
//    void publishBeforeConnectShouldCloseChannel() {
//        EmbeddedChannel ch = new EmbeddedChannel(new MqttProtocolHandler());
//        ch.writeInbound(publishPacket("dev/topic", new byte[]{0x01}));
//        assertFalse(ch.isOpen());
//    }
//
//    @Test
//    void subscribeWithInvalidFlagsShouldCloseChannel() {
//        EmbeddedChannel ch = new EmbeddedChannel(new MqttProtocolHandler());
//        ch.writeInbound(connectPacket("sub-invalid-flags", 0x04));
//        ch.readOutbound().release();
//
//        ByteBuf malformed = Unpooled.buffer();
//        malformed.writeByte(0x80); // SUBSCRIBE with illegal flags (must be 0x82)
//        malformed.writeByte(0x05);
//        malformed.writeShort(1);
//        malformed.writeShort(1);
//        malformed.writeByte((byte) 'a');
//        malformed.writeByte(0);
//        ch.writeInbound(malformed);
//        assertFalse(ch.isOpen());
//    }
//
//    @Test
//    void publishQoS2ShouldCloseChannel() {
//        MqttProtocolHandler handler = new MqttProtocolHandler();
//        EmbeddedChannel sub = new EmbeddedChannel(handler);
//        EmbeddedChannel pub = new EmbeddedChannel(handler);
//
//        sub.writeInbound(connectPacket("sub-qos2", 0x04));
//        sub.readOutbound().release();
//        pub.writeInbound(connectPacket("pub-qos2", 0x04));
//        pub.readOutbound().release();
//
//        sub.writeInbound(subscribePacket(1, "q2/t", 1));
//        sub.readOutbound().release();
//
//        pub.writeInbound(publishPacketQoS2("q2/t", new byte[]{0x66}, 200));
//        ByteBuf pubRec = pub.readOutbound();
//        assertNotNull(pubRec);
//        assertEquals(0x50, pubRec.readUnsignedByte());
//        assertEquals(0x02, pubRec.readUnsignedByte());
//        assertEquals(200, pubRec.readUnsignedShort());
//        pubRec.release();
//
//        // 收到 PUBREL 前不应向订阅者投递
//        assertNull(sub.readOutbound());
//
//        pub.writeInbound(pubRelPacket(200));
//        ByteBuf pubComp = pub.readOutbound();
//        assertNotNull(pubComp);
//        assertEquals(0x70, pubComp.readUnsignedByte());
//        assertEquals(0x02, pubComp.readUnsignedByte());
//        assertEquals(200, pubComp.readUnsignedShort());
//        pubComp.release();
//
//        ByteMsg delivered = readPublishFromSub(sub);
//        assertEquals("q2/t", delivered.topic);
//        assertEquals(1, delivered.payload.length);
//        assertEquals(0x66, delivered.payload[0] & 0xFF);
//
//        assertFalse(sub.finish());
//        assertFalse(pub.finish());
//    }
//
//    @Test
//    void duplicatePubRelShouldBeIdempotentAndStillReturnPubComp() {
//        MqttProtocolHandler handler = new MqttProtocolHandler();
//        EmbeddedChannel sub = new EmbeddedChannel(handler);
//        EmbeddedChannel pub = new EmbeddedChannel(handler);
//
//        sub.writeInbound(connectPacket("sub-qos2-dup-rel", 0x04));
//        sub.readOutbound().release();
//        pub.writeInbound(connectPacket("pub-qos2-dup-rel", 0x04));
//        pub.readOutbound().release();
//        sub.writeInbound(subscribePacket(1, "q2/dup", 1));
//        sub.readOutbound().release();
//
//        pub.writeInbound(publishPacketQoS2("q2/dup", new byte[]{0x10}, 201));
//        ByteBuf pubRec = pub.readOutbound();
//        assertNotNull(pubRec);
//        pubRec.release();
//
//        pub.writeInbound(pubRelPacket(201));
//        ByteBuf firstPubComp = pub.readOutbound();
//        assertNotNull(firstPubComp);
//        assertEquals(0x70, firstPubComp.readUnsignedByte());
//        firstPubComp.release();
//        assertNotNull(sub.readOutbound()).release();
//
//        pub.writeInbound(pubRelPacket(201));
//        ByteBuf secondPubComp = pub.readOutbound();
//        assertNotNull(secondPubComp);
//        assertEquals(0x70, secondPubComp.readUnsignedByte());
//        secondPubComp.release();
//        assertNull(sub.readOutbound());
//
//        assertFalse(sub.finish());
//        assertFalse(pub.finish());
//    }
//
//    @Test
//    void outOfOrderPubRelWithoutPendingPublishShouldReturnPubCompAndNoDelivery() {
//        MqttProtocolHandler handler = new MqttProtocolHandler();
//        EmbeddedChannel sub = new EmbeddedChannel(handler);
//        EmbeddedChannel pub = new EmbeddedChannel(handler);
//
//        sub.writeInbound(connectPacket("sub-qos2-ofo", 0x04));
//        sub.readOutbound().release();
//        pub.writeInbound(connectPacket("pub-qos2-ofo", 0x04));
//        pub.readOutbound().release();
//        sub.writeInbound(subscribePacket(1, "q2/ofo", 1));
//        sub.readOutbound().release();
//
//        pub.writeInbound(pubRelPacket(999));
//        ByteBuf pubComp = pub.readOutbound();
//        assertNotNull(pubComp);
//        assertEquals(0x70, pubComp.readUnsignedByte());
//        assertEquals(0x02, pubComp.readUnsignedByte());
//        assertEquals(999, pubComp.readUnsignedShort());
//        pubComp.release();
//        assertNull(sub.readOutbound());
//
//        assertFalse(sub.finish());
//        assertFalse(pub.finish());
//    }
//
//    @Test
//    void duplicateQoS2PublishAfterCompletionShouldNotDeliverTwice() {
//        MqttProtocolHandler handler = new MqttProtocolHandler();
//        EmbeddedChannel sub = new EmbeddedChannel(handler);
//        EmbeddedChannel pub = new EmbeddedChannel(handler);
//
//        sub.writeInbound(connectPacket("sub-qos2-dedup", 0x04));
//        sub.readOutbound().release();
//        pub.writeInbound(connectPacket("pub-qos2-dedup", 0x04));
//        pub.readOutbound().release();
//        sub.writeInbound(subscribePacket(1, "q2/dedup", 1));
//        sub.readOutbound().release();
//
//        pub.writeInbound(publishPacketQoS2("q2/dedup", new byte[]{0x33}, 301));
//        ByteBuf rec1 = pub.readOutbound();
//        assertNotNull(rec1);
//        rec1.release();
//        pub.writeInbound(pubRelPacket(301));
//        ByteBuf comp1 = pub.readOutbound();
//        assertNotNull(comp1);
//        comp1.release();
//        ByteBuf firstDelivery = sub.readOutbound();
//        assertNotNull(firstDelivery);
//        firstDelivery.release();
//
//        // 同一 packetId 重复 PUBLISH + PUBREL，应被去重窗口拦住，不再投递。
//        pub.writeInbound(publishPacketQoS2("q2/dedup", new byte[]{0x33}, 301));
//        ByteBuf rec2 = pub.readOutbound();
//        assertNotNull(rec2);
//        rec2.release();
//        pub.writeInbound(pubRelPacket(301));
//        ByteBuf comp2 = pub.readOutbound();
//        assertNotNull(comp2);
//        comp2.release();
//        assertNull(sub.readOutbound());
//
//        assertFalse(sub.finish());
//        assertFalse(pub.finish());
//    }
//
//    @Test
//    void duplicateQoS2PublishBeforePubRelShouldNotDoubleCountPending() {
//        MqttProtocolHandler handler = new MqttProtocolHandler();
//        EmbeddedChannel pub = new EmbeddedChannel(handler);
//        pub.writeInbound(connectPacket("pub-qos2-dup-before-rel", 0x04));
//        pub.readOutbound().release();
//
//        assertEquals(0L, MqttProtocolHandler.metricQos2InPending());
//        pub.writeInbound(publishPacketQoS2("q2/dup-before", new byte[]{0x01}, 501));
//        ByteBuf rec1 = pub.readOutbound();
//        assertNotNull(rec1);
//        rec1.release();
//        assertEquals(1L, MqttProtocolHandler.metricQos2InPending());
//
//        pub.writeInbound(publishPacketQoS2("q2/dup-before", new byte[]{0x01}, 501));
//        ByteBuf rec2 = pub.readOutbound();
//        assertNotNull(rec2);
//        rec2.release();
//        // 同一个 packetId 在 PUBREL 前重复上报，只替换 pending，不应累计 pending 数。
//        assertEquals(1L, MqttProtocolHandler.metricQos2InPending());
//
//        pub.writeInbound(pubRelPacket(501));
//        ByteBuf comp = pub.readOutbound();
//        assertNotNull(comp);
//        comp.release();
//        assertEquals(0L, MqttProtocolHandler.metricQos2InPending());
//        assertFalse(pub.finish());
//    }
//
//    @Test
//    void qos2PendingMetricShouldIncreaseAndDecreaseAroundPubRel() {
//        MqttProtocolHandler handler = new MqttProtocolHandler();
//        EmbeddedChannel pub = new EmbeddedChannel(handler);
//        pub.writeInbound(connectPacket("pub-qos2-metric", 0x04));
//        pub.readOutbound().release();
//
//        assertEquals(0L, MqttProtocolHandler.metricQos2InPending());
//        pub.writeInbound(publishPacketQoS2("q2/metric", new byte[]{0x20}, 401));
//        ByteBuf pubRec = pub.readOutbound();
//        assertNotNull(pubRec);
//        pubRec.release();
//        assertEquals(1L, MqttProtocolHandler.metricQos2InPending());
//
//        pub.writeInbound(pubRelPacket(401));
//        ByteBuf pubComp = pub.readOutbound();
//        assertNotNull(pubComp);
//        pubComp.release();
//        assertEquals(0L, MqttProtocolHandler.metricQos2InPending());
//        assertTrue(MqttProtocolHandler.metricQos2InCompletedTotal() >= 1);
//        assertFalse(pub.finish());
//    }
//
//    @Test
//    void qos2PendingMetricShouldBeClearedOnDisconnect() {
//        MqttProtocolHandler handler = new MqttProtocolHandler();
//        EmbeddedChannel pub = new EmbeddedChannel(handler);
//        pub.writeInbound(connectPacket("pub-qos2-close", 0x04));
//        pub.readOutbound().release();
//
//        assertEquals(0L, MqttProtocolHandler.metricQos2InPending());
//        pub.writeInbound(publishPacketQoS2("q2/close", new byte[]{0x51}, 402));
//        ByteBuf rec = pub.readOutbound();
//        assertNotNull(rec);
//        rec.release();
//        assertEquals(1L, MqttProtocolHandler.metricQos2InPending());
//
//        pub.close();
//        assertEquals(0L, MqttProtocolHandler.metricQos2InPending());
//        assertFalse(pub.finish());
//    }
//
//    @Test
//    void retainedMessageShouldReplayOnLaterSubscribe() {
//        MqttProtocolHandler handler = new MqttProtocolHandler();
//        EmbeddedChannel pub = new EmbeddedChannel(handler);
//        EmbeddedChannel sub = new EmbeddedChannel(handler);
//
//        pub.writeInbound(connectPacket("pub-retain", 0x04));
//        pub.readOutbound().release();
//        pub.writeInbound(publishPacketRetainQoS0("retain/t", new byte[]{0x41}));
//
//        sub.writeInbound(connectPacket("sub-retain", 0x04));
//        sub.readOutbound().release();
//        sub.writeInbound(subscribePacket(1, "retain/#", 0));
//        ByteBuf subAck = sub.readOutbound();
//        assertNotNull(subAck);
//        subAck.release();
//
//        ByteBuf retained = sub.readOutbound();
//        assertNotNull(retained);
//        assertEquals(0x31, retained.readUnsignedByte()); // retain flag must be set
//        retained.release();
//
//        assertFalse(pub.finish());
//        assertFalse(sub.finish());
//    }
//
//    @Test
//    void zeroByteRetainedPublishShouldClearStoredRetain() {
//        MqttProtocolHandler handler = new MqttProtocolHandler();
//        EmbeddedChannel pub = new EmbeddedChannel(handler);
//        EmbeddedChannel sub = new EmbeddedChannel(handler);
//
//        pub.writeInbound(connectPacket("pub-retain-clear", 0x04));
//        pub.readOutbound().release();
//        pub.writeInbound(publishPacketRetainQoS0("retain/clear", new byte[]{0x55}));
//        pub.writeInbound(publishPacketRetainQoS0("retain/clear", new byte[0]));
//
//        sub.writeInbound(connectPacket("sub-retain-clear", 0x04));
//        sub.readOutbound().release();
//        sub.writeInbound(subscribePacket(1, "retain/clear", 0));
//        ByteBuf subAck = sub.readOutbound();
//        assertNotNull(subAck);
//        subAck.release();
//        assertNull(sub.readOutbound());
//
//        assertFalse(pub.finish());
//        assertFalse(sub.finish());
//    }
//
//    @Test
//    void nonRetainedPublishShouldNotOverwriteExistingRetainedMessage() {
//        MqttProtocolHandler handler = new MqttProtocolHandler();
//        EmbeddedChannel pub = new EmbeddedChannel(handler);
//        EmbeddedChannel sub = new EmbeddedChannel(handler);
//
//        pub.writeInbound(connectPacket("pub-retain-keep", 0x04));
//        pub.readOutbound().release();
//        pub.writeInbound(publishPacketRetainQoS0("retain/keep", new byte[]{0x11}));
//        pub.writeInbound(publishPacket("retain/keep", new byte[]{0x22})); // retain=0
//
//        sub.writeInbound(connectPacket("sub-retain-keep", 0x04));
//        sub.readOutbound().release();
//        sub.writeInbound(subscribePacket(1, "retain/keep", 0));
//        ByteBuf subAck = sub.readOutbound();
//        assertNotNull(subAck);
//        subAck.release();
//
//        ByteBuf retained = sub.readOutbound();
//        assertNotNull(retained);
//        assertEquals(0x31, retained.readUnsignedByte());
//        retained.readUnsignedByte();
//        retained.readUnsignedShort();
//        retained.skipBytes("retain/keep".length());
//        byte b = retained.readByte();
//        assertEquals(0x11, b & 0xFF);
//        retained.release();
//
//        assertFalse(pub.finish());
//        assertFalse(sub.finish());
//    }
//
//    @Test
//    void cleanSessionFalseShouldQueueOfflineAndReplayAfterReconnect() {
//        MqttProtocolHandler handler = new MqttProtocolHandler();
//        EmbeddedChannel sub1 = new EmbeddedChannel(handler);
//        EmbeddedChannel pub = new EmbeddedChannel(handler);
//
//        sub1.writeInbound(connectPacketWithCleanSession("sub-persist-1", 0x04, false));
//        ByteBuf connAck1 = sub1.readOutbound();
//        assertNotNull(connAck1);
//        connAck1.release();
//        sub1.writeInbound(subscribePacket(1, "persist/t", 0));
//        ByteBuf subAck = sub1.readOutbound();
//        assertNotNull(subAck);
//        subAck.release();
//
//        // 模拟离线
//        sub1.close();
//
//        pub.writeInbound(connectPacket("pub-persist", 0x04));
//        ByteBuf pubConn = pub.readOutbound();
//        assertNotNull(pubConn);
//        pubConn.release();
//        pub.writeInbound(publishPacket("persist/t", new byte[]{0x21, 0x22}));
//
//        EmbeddedChannel sub2 = new EmbeddedChannel(handler);
//        sub2.writeInbound(connectPacketWithCleanSession("sub-persist-1", 0x04, false));
//        ByteBuf connAck2 = sub2.readOutbound();
//        assertNotNull(connAck2);
//        connAck2.release();
//
//        ByteBuf replayed = sub2.readOutbound();
//        assertNotNull(replayed);
//        assertEquals(0x30, replayed.readUnsignedByte() & 0xF0);
//        replayed.release();
//
//        assertFalse(pub.finish());
//        assertFalse(sub1.finish());
//        assertFalse(sub2.finish());
//    }
//
//    @Test
//    void cleanSessionTrueShouldNotReplayOfflineMessagesAfterReconnect() {
//        MqttProtocolHandler handler = new MqttProtocolHandler();
//        EmbeddedChannel sub1 = new EmbeddedChannel(handler);
//        EmbeddedChannel pub = new EmbeddedChannel(handler);
//
//        sub1.writeInbound(connectPacketWithCleanSession("sub-clean-true", 0x04, true));
//        ByteBuf connAck1 = sub1.readOutbound();
//        assertNotNull(connAck1);
//        connAck1.release();
//        sub1.writeInbound(subscribePacket(1, "clean/t", 0));
//        ByteBuf subAck1 = sub1.readOutbound();
//        assertNotNull(subAck1);
//        subAck1.release();
//
//        sub1.close();
//
//        pub.writeInbound(connectPacket("pub-clean-true", 0x04));
//        ByteBuf pubConn = pub.readOutbound();
//        assertNotNull(pubConn);
//        pubConn.release();
//        pub.writeInbound(publishPacket("clean/t", new byte[]{0x31}));
//
//        EmbeddedChannel sub2 = new EmbeddedChannel(handler);
//        sub2.writeInbound(connectPacketWithCleanSession("sub-clean-true", 0x04, true));
//        ByteBuf connAck2 = sub2.readOutbound();
//        assertNotNull(connAck2);
//        connAck2.release();
//        // cleanSession=true: 不应恢复旧订阅，也不应补发离线消息
//        assertNull(sub2.readOutbound());
//
//        assertFalse(pub.finish());
//        assertFalse(sub1.finish());
//        assertFalse(sub2.finish());
//    }
//
//    @Test
//    void sessionStoreShouldWriteVersionHeaderAndLoadByHeader() throws IOException {
//        MqttProtocolHandler handler = new MqttProtocolHandler();
//        EmbeddedChannel sub = new EmbeddedChannel(handler);
//        sub.writeInbound(connectPacketWithCleanSession("sub-header", 0x04, false));
//        ByteBuf connAck = sub.readOutbound();
//        assertNotNull(connAck);
//        connAck.release();
//        sub.writeInbound(subscribePacket(1, "hdr/t", 0));
//        ByteBuf subAck = sub.readOutbound();
//        assertNotNull(subAck);
//        subAck.release();
//        sub.close();
//
//        assertTrue(Files.exists(SESSION_STORE_PATH));
//        String firstLine = Files.readAllLines(SESSION_STORE_PATH, StandardCharsets.UTF_8).get(0);
//        assertEquals("#session-store\tv1", firstLine);
//
//        // 篡改为不兼容头后，应忽略加载。
//        Files.writeString(SESSION_STORE_PATH, "#session-store\tv9\nSUB\tsub-header\thdr/t\t0\n", StandardCharsets.UTF_8);
//        MqttProtocolHandler.reloadSessionsForTests();
//
//        EmbeddedChannel reconnect = new EmbeddedChannel(new MqttProtocolHandler());
//        reconnect.writeInbound(connectPacketWithCleanSession("sub-header", 0x04, false));
//        ByteBuf reconnectConnAck = reconnect.readOutbound();
//        assertNotNull(reconnectConnAck);
//        reconnectConnAck.release();
//        reconnect.writeInbound(publishPacket("hdr/t", new byte[]{0x11}));
//        assertNull(reconnect.readOutbound());
//    }
//
//    private static ByteMsg readPublishFromSub(EmbeddedChannel sub) {
//        ByteBuf out = sub.readOutbound();
//        assertNotNull(out);
//        int fh = out.readUnsignedByte();
//        int rl = readRl(out);
//        int tl = out.readUnsignedShort();
//        byte[] tb = new byte[tl];
//        out.readBytes(tb);
//        int pid = out.readUnsignedShort();
//        byte[] pl = new byte[out.readableBytes()];
//        out.readBytes(pl);
//        out.release();
//        return new ByteMsg(fh, new String(tb, StandardCharsets.UTF_8), pid, pl);
//    }
//
//    private static int readRl(ByteBuf buf) {
//        int mult = 1;
//        int v = 0;
//        int b;
//        do {
//            b = buf.readUnsignedByte();
//            v += (b & 127) * mult;
//            mult *= 128;
//        } while ((b & 128) != 0);
//        return v;
//    }
//
//    private static final class ByteMsg {
//        final int fixedHeader;
//        final String topic;
//        final int packetId;
//        final byte[] payload;
//
//        ByteMsg(int fixedHeader, String topic, int packetId, byte[] payload) {
//            this.fixedHeader = fixedHeader;
//            this.topic = topic;
//            this.packetId = packetId;
//            this.payload = payload;
//        }
//    }
//
//    private static ByteBuf connectPacket(String clientId, int protocolLevel) {
//        byte[] client = clientId.getBytes(StandardCharsets.UTF_8);
//        int remainingLength = 10 + 2 + client.length;
//        ByteBuf buf = Unpooled.buffer(2 + remainingLength);
//        buf.writeByte(0x10);
//        buf.writeByte(remainingLength);
//        buf.writeShort(4);
//        buf.writeBytes(new byte[]{'M', 'Q', 'T', 'T'});
//        buf.writeByte(protocolLevel);
//        buf.writeByte(0x02); // clean session
//        buf.writeShort(60);
//        buf.writeShort(client.length);
//        buf.writeBytes(client);
//        return buf;
//    }
//
//    private static ByteBuf connectPacket5(String clientId) {
//        byte[] client = clientId.getBytes(StandardCharsets.UTF_8);
//        // MQTT5: variable header 10 bytes + properties length(1) + payload(clientId)
//        int remainingLength = 10 + 1 + 2 + client.length;
//        ByteBuf buf = Unpooled.buffer(2 + remainingLength);
//        buf.writeByte(0x10);
//        buf.writeByte(remainingLength);
//        buf.writeShort(4);
//        buf.writeBytes(new byte[]{'M', 'Q', 'T', 'T'});
//        buf.writeByte(0x05);
//        buf.writeByte(0x02); // clean session
//        buf.writeShort(60);
//        buf.writeByte(0x00); // properties length
//        buf.writeShort(client.length);
//        buf.writeBytes(client);
//        return buf;
//    }
//
//    private static ByteBuf connectPacketWithCleanSession(String clientId, int protocolLevel, boolean cleanSession) {
//        byte[] client = clientId.getBytes(StandardCharsets.UTF_8);
//        int remainingLength = 10 + 2 + client.length;
//        ByteBuf buf = Unpooled.buffer(2 + remainingLength);
//        buf.writeByte(0x10);
//        buf.writeByte(remainingLength);
//        buf.writeShort(4);
//        buf.writeBytes(new byte[]{'M', 'Q', 'T', 'T'});
//        buf.writeByte(protocolLevel);
//        buf.writeByte(cleanSession ? 0x02 : 0x00);
//        buf.writeShort(60);
//        buf.writeShort(client.length);
//        buf.writeBytes(client);
//        return buf;
//    }
//
//    private static ByteBuf connectPacketWithAuth(String clientId, int protocolLevel, String username, String password) {
//        byte[] client = clientId.getBytes(StandardCharsets.UTF_8);
//        byte[] user = username.getBytes(StandardCharsets.UTF_8);
//        byte[] pass = password.getBytes(StandardCharsets.UTF_8);
//        int remainingLength = 10 + (2 + client.length) + (2 + user.length) + (2 + pass.length);
//        ByteBuf buf = Unpooled.buffer(2 + remainingLength);
//        buf.writeByte(0x10);
//        buf.writeByte(remainingLength);
//        buf.writeShort(4);
//        buf.writeBytes(new byte[]{'M', 'Q', 'T', 'T'});
//        buf.writeByte(protocolLevel);
//        buf.writeByte(0xC2); // username + password + clean session
//        buf.writeShort(60);
//        buf.writeShort(client.length);
//        buf.writeBytes(client);
//        buf.writeShort(user.length);
//        buf.writeBytes(user);
//        buf.writeShort(pass.length);
//        buf.writeBytes(pass);
//        return buf;
//    }
//
//    private static ByteBuf connectPacketWithWill(String clientId,
//                                                int protocolLevel,
//                                                String willTopic,
//                                                byte[] willPayload,
//                                                int willQos,
//                                                boolean willRetain) {
//        byte[] client = clientId.getBytes(StandardCharsets.UTF_8);
//        byte[] wt = willTopic.getBytes(StandardCharsets.UTF_8);
//        int flags = 0x02; // clean session
//        flags |= 0x04; // will flag
//        flags |= ((willQos & 0x03) << 3);
//        if (willRetain) {
//            flags |= 0x20;
//        }
//
//        int remainingLength = 10
//                + (2 + client.length)
//                + (2 + wt.length)
//                + (2 + willPayload.length);
//        ByteBuf buf = Unpooled.buffer(2 + remainingLength);
//        buf.writeByte(0x10);
//        buf.writeByte(remainingLength);
//        buf.writeShort(4);
//        buf.writeBytes(new byte[]{'M', 'Q', 'T', 'T'});
//        buf.writeByte(protocolLevel);
//        buf.writeByte(flags);
//        buf.writeShort(60);
//        buf.writeShort(client.length);
//        buf.writeBytes(client);
//        buf.writeShort(wt.length);
//        buf.writeBytes(wt);
//        buf.writeShort(willPayload.length);
//        buf.writeBytes(willPayload);
//        return buf;
//    }
//
//    private static ByteBuf disconnectPacket() {
//        return Unpooled.wrappedBuffer(new byte[]{(byte) 0xE0, 0x00});
//    }
//
//    private static ByteBuf unsubscribePacket(int packetId, String topicFilter) {
//        byte[] topicBytes = topicFilter.getBytes(StandardCharsets.UTF_8);
//        int remainingLength = 2 + 2 + topicBytes.length;
//        ByteBuf buf = Unpooled.buffer(2 + remainingLength);
//        buf.writeByte(0xA2); // UNSUBSCRIBE flags=0x02
//        buf.writeByte(remainingLength);
//        buf.writeShort(packetId);
//        buf.writeShort(topicBytes.length);
//        buf.writeBytes(topicBytes);
//        return buf;
//    }
//
//    private static ByteBuf subscribePacket(int packetId, String topic, int qos) {
//        byte[] topicBytes = topic.getBytes(StandardCharsets.UTF_8);
//        int remainingLength = 2 + 2 + topicBytes.length + 1;
//        ByteBuf buf = Unpooled.buffer(2 + remainingLength);
//        buf.writeByte(0x82);
//        buf.writeByte(remainingLength);
//        buf.writeShort(packetId);
//        buf.writeShort(topicBytes.length);
//        buf.writeBytes(topicBytes);
//        buf.writeByte(qos);
//        return buf;
//    }
//
//    private static ByteBuf subscribePacket5(int packetId, String topic, int qos) {
//        byte[] topicBytes = topic.getBytes(StandardCharsets.UTF_8);
//        // MQTT5: packetId(2) + properties length(1) + (topic + qos)
//        int remainingLength = 2 + 1 + 2 + topicBytes.length + 1;
//        ByteBuf buf = Unpooled.buffer(2 + remainingLength);
//        buf.writeByte(0x82);
//        buf.writeByte(remainingLength);
//        buf.writeShort(packetId);
//        buf.writeByte(0x00); // properties length
//        buf.writeShort(topicBytes.length);
//        buf.writeBytes(topicBytes);
//        buf.writeByte(qos);
//        return buf;
//    }
//
//    private static ByteBuf publishPacket(String topic, byte[] payload) {
//        byte[] topicBytes = topic.getBytes(StandardCharsets.UTF_8);
//        int remainingLength = 2 + topicBytes.length + payload.length;
//        ByteBuf buf = Unpooled.buffer(2 + remainingLength);
//        buf.writeByte(0x30);
//        buf.writeByte(remainingLength);
//        buf.writeShort(topicBytes.length);
//        buf.writeBytes(topicBytes);
//        buf.writeBytes(payload);
//        return buf;
//    }
//
//    private static ByteBuf publishPacket5(String topic, byte[] payload) {
//        byte[] topicBytes = topic.getBytes(StandardCharsets.UTF_8);
//        // MQTT5 QoS0: topic + properties length(1) + payload
//        int remainingLength = 2 + topicBytes.length + 1 + payload.length;
//        ByteBuf buf = Unpooled.buffer(2 + remainingLength);
//        buf.writeByte(0x30);
//        buf.writeByte(remainingLength);
//        buf.writeShort(topicBytes.length);
//        buf.writeBytes(topicBytes);
//        buf.writeByte(0x00); // properties length
//        buf.writeBytes(payload);
//        return buf;
//    }
//
//    private static ByteBuf publishPacketQoS1(String topic, byte[] payload, int packetId) {
//        byte[] topicBytes = topic.getBytes(StandardCharsets.UTF_8);
//        int remainingLength = 2 + topicBytes.length + 2 + payload.length;
//        ByteBuf buf = Unpooled.buffer(2 + remainingLength);
//        buf.writeByte(0x32);
//        buf.writeByte(remainingLength);
//        buf.writeShort(topicBytes.length);
//        buf.writeBytes(topicBytes);
//        buf.writeShort(packetId);
//        buf.writeBytes(payload);
//        return buf;
//    }
//
//    private static ByteBuf publishPacketQoS2(String topic, byte[] payload, int packetId) {
//        byte[] topicBytes = topic.getBytes(StandardCharsets.UTF_8);
//        int remainingLength = 2 + topicBytes.length + 2 + payload.length;
//        ByteBuf buf = Unpooled.buffer(2 + remainingLength);
//        buf.writeByte(0x34);
//        buf.writeByte(remainingLength);
//        buf.writeShort(topicBytes.length);
//        buf.writeBytes(topicBytes);
//        buf.writeShort(packetId);
//        buf.writeBytes(payload);
//        return buf;
//    }
//
//    private static ByteBuf pubRelPacket(int packetId) {
//        ByteBuf buf = Unpooled.buffer(4);
//        buf.writeByte(0x62);
//        buf.writeByte(0x02);
//        buf.writeShort(packetId);
//        return buf;
//    }
//
//    private static ByteBuf publishPacketRetainQoS0(String topic, byte[] payload) {
//        byte[] topicBytes = topic.getBytes(StandardCharsets.UTF_8);
//        int remainingLength = 2 + topicBytes.length + payload.length;
//        ByteBuf buf = Unpooled.buffer(2 + remainingLength);
//        buf.writeByte(0x31); // retain=1, qos0
//        buf.writeByte(remainingLength);
//        buf.writeShort(topicBytes.length);
//        buf.writeBytes(topicBytes);
//        buf.writeBytes(payload);
//        return buf;
//    }
//
//    private static ByteBuf pubAckPacket(int packetId) {
//        ByteBuf buf = Unpooled.buffer(4);
//        buf.writeByte(0x40);
//        buf.writeByte(0x02);
//        buf.writeShort(packetId);
//        return buf;
//    }
//}
