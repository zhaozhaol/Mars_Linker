package com.mars.linker.broker.netty;

import com.mars.linker.broker.netty.protocol.*;
import com.mars.linker.broker.netty.trace.TraceContext;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.AttributeKey;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.LongConsumer;
import java.util.concurrent.TimeUnit;
import com.mars.linker.broker.netty.acl.AclProvider;
import com.mars.linker.broker.netty.acl.PrefixAclProvider;
import com.mars.linker.broker.netty.auth.AuthProvider;
import com.mars.linker.broker.netty.auth.StaticAuthProvider;
import com.mars.linker.broker.netty.store.BoundedRetainStore;
import com.mars.linker.broker.netty.store.FileRetainStore;
import com.mars.linker.broker.netty.store.RetainStore;
import com.mars.linker.broker.netty.store.SessionService;
import com.mars.linker.broker.ui.rejection.RejectionMessageCollector;
import com.mars.linker.broker.ui.rejection.model.RejectionReason;

/**
 * MQTT 3.1.1 协议处理（当前为阶段 4/5 的持续演进实现）。
 * <p>
 * <b>职责边界</b>：在 {@link MqttFrameDecoder} 已切出<b>完整 MQTT 帧</b>的前提下，解析固定头与负载，
 * 维护订阅表与连接表，完成本阶段支持的控制报文与 PUBLISH 分发。
 * </p>
 * <p>
 * <b>已实现</b>：CONNECT/CONNACK、PINGREQ/PINGRESP、SUBSCRIBE/SUBACK（精确/通配符/{@code $share}）、
 * UNSUBSCRIBE/UNSUBACK、PUBLISH QoS0/1/2（QoS2 为上行握手）、Will、用户名密码鉴权、ACL（静态前缀策略）、
 * Retain/会话离线消息文件持久化（PoC）、下行 QoS1 inflight 与可选重传。
 * </p>
 * <p>
     * <b>未实现（后续阶段）</b>：集群会话一致性、更完整的 MQTT 5 语义等。
     * 认证/ ACL 为可插拔；HTTP 动态鉴权见 {@code AuthProviderFactory} / {@code HttpAuthProvider}。
 * </p>
 * <p>
 * <b>测试入口</b>：协议回归见 {@code MqttProtocolHandlerTest}，集成对拍见 {@code BrokerParityIntegrationTest}。
 * </p>
 *
 * @see MqttFrameDecoder
 */
@ChannelHandler.Sharable
public class MqttProtocolHandler extends SimpleChannelInboundHandler<ByteBuf> {

    private static final Logger log = LoggerFactory.getLogger(MqttProtocolHandler.class);
    /** 延迟发布主题前缀：$delayed/{seconds}/{real_topic}。 */
    private static final String DELAYED_TOPIC_PREFIX = "$delayed/";

    /**
     * 是否启用 CONNECT 用户名/密码鉴权。
     * <p>
     * 说明：为简化阶段 3/4 的最小闭环，这里采用“单账号”校验；后续可替换为 DB/Redis/HTTP 鉴权。
     * </p>
     */
    private final AuthProvider authProvider;
    private final boolean qos1RetransmitEnabled;
    private final long qos1RetransmitIntervalMs;
    private final int qos1RetransmitMaxAttempts;
    private final QoS1OutboundService qos1Outbound;
    private final QoS2InboundService qos2Inbound;
    private final QoS2OutboundService qos2Outbound;
    private final DeviceLifecyclePublisher deviceLifecyclePublisher;
    private final EventNotifyRouter eventNotifyRouter;
    private final int maxConnections;
    private final AclProvider aclProvider;
    private volatile RejectionMessageCollector rejectionMessageCollector;
    private volatile boolean serverShuttingDown = false;
    private final TopicRateLimiter topicRateLimiter;
    private final HashedWheelTimer keepAliveTimer = new HashedWheelTimer(
            r -> { Thread t = new Thread(r, "keepalive-checker"); t.setDaemon(true); return t; },
            1, TimeUnit.SECONDS, 512, true
    );
    private final HashedWheelTimer retransmitTimer = new HashedWheelTimer(
            r -> { Thread t = new Thread(r, "qos-retransmit"); t.setDaemon(true); return t; },
            1, TimeUnit.SECONDS, 512, true
    );
    { keepAliveTimer.start(); retransmitTimer.start(); }

    // 指标：使用 LongAdder 作为轻量并发计数器，由 MqttBrokerMetricsBinder 暴露到 Micrometer。
    private final LongAdder METRIC_CONNECTIONS_ACTIVE = new LongAdder();
    private final LongAdder METRIC_CONNECT_ACCEPTED_TOTAL = new LongAdder();
    private final LongAdder METRIC_CONNECT_REJECTED_TOTAL = new LongAdder();
    private final LongAdder METRIC_PUBLISH_IN_TOTAL = new LongAdder();
    private final LongAdder METRIC_PUBLISH_OUT_TOTAL = new LongAdder();
    private final LongAdder METRIC_QOS2_IN_PENDING = new LongAdder();
    private final LongAdder METRIC_QOS2_IN_COMPLETED_TOTAL = new LongAdder();
    private final LongAdder METRIC_ACL_SUB_DENY_TOTAL = new LongAdder();
    private final LongAdder METRIC_ACL_PUB_DENY_TOTAL = new LongAdder();

    private static volatile MqttProtocolHandler INSTANCE;

    /** 本连接已订阅的主题集合（用于断线时从全局路由表摘除）。 */
    private static final AttributeKey<Set<String>> TOPIC_SUBSCRIPTIONS = AttributeKey.valueOf("mqtt_topic_subscriptions");
    /** 本连接对每个主题的<b>授予 QoS</b>（SUBACK 返回值），用于与发布端 QoS 取 min 后投递。 */
    private static final AttributeKey<ConcurrentHashMap<String, Integer>> SUBSCRIPTION_QOS =
            AttributeKey.valueOf("mqtt_subscription_qos");
    /** 实例：订阅注册表（精确/通配符/$share + 轮询）。 */
    private final SubscriptionRegistry SUBSCRIPTION_REGISTRY;
    /** 实例：连接 ID → Netty 上下文，用于向订阅者写回 PUBLISH。 */
    private final Map<ChannelId, ChannelHandlerContext> channels;
    /** 实例：Session 管理（含离线队列与持久化）。 */
    private final SessionService SESSION_SERVICE;
    /** 实例：clientId → 当前活跃 channelId（用于踢掉旧连接与恢复订阅）。 */
    private final Map<String, ChannelId> CLIENT_TO_CHANNEL;
    /** 实例：channelId → clientId（用于离线期间仍可把消息入队到 session）。 */
    private final Map<ChannelId, String> CHANNEL_TO_CLIENT;
    /** 实例：Retain 消息持久化存储。 */
    private final RetainStore RETAIN_STORE;
    /** 实例：原子活跃连接计数，用于上限检查和指标同步。 */
    private final AtomicInteger activeConnectionCount;

    public MqttProtocolHandler() {
        this(false, null, null, false, 5_000, 3, false, Collections.emptyList(), Collections.emptyList(),
                false, Collections.emptyList(), Collections.emptyList(), 0, 1024,
                new EventNotifyRouter(false, Collections.emptyMap(), Collections.emptyList()));
    }

    public MqttProtocolHandler(boolean authEnabled, String authUsername, String authPassword) {
        this(authEnabled, authUsername, authPassword, false, 5_000, 3, false, Collections.emptyList(), Collections.emptyList(),
                false, Collections.emptyList(), Collections.emptyList(), 0, 1024,
                new EventNotifyRouter(false, Collections.emptyMap(), Collections.emptyList()));
    }

    public MqttProtocolHandler(boolean authEnabled,
                               String authUsername,
                               String authPassword,
                               boolean qos1RetransmitEnabled,
                               long qos1RetransmitIntervalMs,
                               int qos1RetransmitMaxAttempts) {
        this(authEnabled, authUsername, authPassword, qos1RetransmitEnabled, qos1RetransmitIntervalMs, qos1RetransmitMaxAttempts,
                false, Collections.emptyList(), Collections.emptyList(), false, Collections.emptyList(), Collections.emptyList(),
                0, 1024, new EventNotifyRouter(false, Collections.emptyMap(), Collections.emptyList()));
    }

    public MqttProtocolHandler(boolean authEnabled,
                               String authUsername,
                               String authPassword,
                               boolean qos1RetransmitEnabled,
                               long qos1RetransmitIntervalMs,
                               int qos1RetransmitMaxAttempts,
                               boolean aclEnabled,
                               List<String> aclAllowSubscribePrefixes,
                               List<String> aclAllowPublishPrefixes) {
        this(authEnabled, authUsername, authPassword, qos1RetransmitEnabled, qos1RetransmitIntervalMs, qos1RetransmitMaxAttempts,
                aclEnabled, aclAllowSubscribePrefixes, aclAllowPublishPrefixes, false, Collections.emptyList(), Collections.emptyList(),
                0, 1024, new EventNotifyRouter(false, Collections.emptyMap(), Collections.emptyList()));
    }

    public MqttProtocolHandler(boolean authEnabled,
                               String authUsername,
                               String authPassword,
                               boolean qos1RetransmitEnabled,
                               long qos1RetransmitIntervalMs,
                               int qos1RetransmitMaxAttempts,
                               boolean aclEnabled,
                               List<String> aclAllowSubscribePrefixes,
                               List<String> aclAllowPublishPrefixes,
                               boolean aclDefaultDeny,
                               List<String> aclDenySubscribePrefixes,
                               List<String> aclDenyPublishPrefixes) {
        this(authEnabled, authUsername, authPassword, qos1RetransmitEnabled, qos1RetransmitIntervalMs, qos1RetransmitMaxAttempts,
                aclEnabled, aclAllowSubscribePrefixes, aclAllowPublishPrefixes, aclDefaultDeny,
                aclDenySubscribePrefixes, aclDenyPublishPrefixes, 0, 1024,
                new EventNotifyRouter(false, Collections.emptyMap(), Collections.emptyList()));
    }

    public MqttProtocolHandler(boolean authEnabled,
                               String authUsername,
                               String authPassword,
                               boolean qos1RetransmitEnabled,
                               long qos1RetransmitIntervalMs,
                               int qos1RetransmitMaxAttempts,
                               boolean aclEnabled,
                               List<String> aclAllowSubscribePrefixes,
                               List<String> aclAllowPublishPrefixes,
                               boolean aclDefaultDeny,
                               List<String> aclDenySubscribePrefixes,
                               List<String> aclDenyPublishPrefixes,
                               int maxConnections,
                               int inboundQos2PendingMax) {
        this(authEnabled, authUsername, authPassword, qos1RetransmitEnabled, qos1RetransmitIntervalMs, qos1RetransmitMaxAttempts,
                aclEnabled, aclAllowSubscribePrefixes, aclAllowPublishPrefixes, aclDefaultDeny,
                aclDenySubscribePrefixes, aclDenyPublishPrefixes, maxConnections, inboundQos2PendingMax,
                new EventNotifyRouter(false, Collections.emptyMap(), Collections.emptyList()));
    }

    public MqttProtocolHandler(boolean authEnabled,
                               String authUsername,
                               String authPassword,
                               boolean qos1RetransmitEnabled,
                               long qos1RetransmitIntervalMs,
                               int qos1RetransmitMaxAttempts,
                               boolean aclEnabled,
                               List<String> aclAllowSubscribePrefixes,
                               List<String> aclAllowPublishPrefixes,
                               boolean aclDefaultDeny,
                               List<String> aclDenySubscribePrefixes,
                               List<String> aclDenyPublishPrefixes,
                               int maxConnections,
                               int inboundQos2PendingMax,
                               EventNotifyRouter eventNotifyRouter) {
        this(new StaticAuthProvider(authEnabled, authUsername, authPassword),
                new PrefixAclProvider(aclEnabled,
                        aclAllowSubscribePrefixes,
                        aclAllowPublishPrefixes,
                        aclDefaultDeny,
                        aclDenySubscribePrefixes,
                        aclDenyPublishPrefixes),
                qos1RetransmitEnabled,
                qos1RetransmitIntervalMs,
                qos1RetransmitMaxAttempts,
                maxConnections,
                inboundQos2PendingMax,
                SessionService.create(null, 5000, 604800000L),
                new BoundedRetainStore(new FileRetainStore(java.nio.file.Paths.get("data", "retain-store.tsv")), 200000, 2592000000L),
                eventNotifyRouter);
    }

    public MqttProtocolHandler(AuthProvider authProvider,
                               AclProvider aclProvider,
                               boolean qos1RetransmitEnabled,
                               long qos1RetransmitIntervalMs,
                               int qos1RetransmitMaxAttempts,
                               int maxConnections,
                               int inboundQos2PendingMax) {
        this(authProvider, aclProvider, qos1RetransmitEnabled, qos1RetransmitIntervalMs, qos1RetransmitMaxAttempts,
                maxConnections, inboundQos2PendingMax,
                SessionService.create(null, 5000, 604800000L),
                new BoundedRetainStore(new FileRetainStore(java.nio.file.Paths.get("data", "retain-store.tsv")), 200000, 2592000000L),
                new EventNotifyRouter(false, Collections.emptyMap(), Collections.emptyList()));
    }

    public MqttProtocolHandler(AuthProvider authProvider,
                               AclProvider aclProvider,
                               boolean qos1RetransmitEnabled,
                               long qos1RetransmitIntervalMs,
                               int qos1RetransmitMaxAttempts,
                               int maxConnections,
                               int inboundQos2PendingMax,
                               SessionService SESSION_SERVICE,
                               RetainStore RETAIN_STORE) {
        this(authProvider, aclProvider, qos1RetransmitEnabled, qos1RetransmitIntervalMs, qos1RetransmitMaxAttempts,
                maxConnections, inboundQos2PendingMax, SESSION_SERVICE, RETAIN_STORE,
                new EventNotifyRouter(false, Collections.emptyMap(), Collections.emptyList()));
    }

    public MqttProtocolHandler(AuthProvider authProvider,
                               AclProvider aclProvider,
                               boolean qos1RetransmitEnabled,
                               long qos1RetransmitIntervalMs,
                               int qos1RetransmitMaxAttempts,
                               int maxConnections,
                               int inboundQos2PendingMax,
                               SessionService SESSION_SERVICE,
                               RetainStore RETAIN_STORE,
                               EventNotifyRouter eventNotifyRouter) {
        this.authProvider = authProvider == null ? new StaticAuthProvider(false, null, null) : authProvider;
        this.aclProvider = aclProvider == null
                ? new PrefixAclProvider(false, Collections.emptyList(), Collections.emptyList(),
                false, Collections.emptyList(), Collections.emptyList())
                : aclProvider;
        this.qos1RetransmitEnabled = qos1RetransmitEnabled;
        this.qos1RetransmitIntervalMs = qos1RetransmitIntervalMs;
        this.qos1RetransmitMaxAttempts = qos1RetransmitMaxAttempts;
        this.maxConnections = maxConnections;
        this.SUBSCRIPTION_REGISTRY = new SubscriptionRegistry();
        this.channels = new ConcurrentHashMap<>(16384);
        this.SESSION_SERVICE = SESSION_SERVICE;
        this.CLIENT_TO_CHANNEL = new ConcurrentHashMap<>(16384);
        this.CHANNEL_TO_CLIENT = new ConcurrentHashMap<>(16384);
        this.RETAIN_STORE = RETAIN_STORE;
        this.activeConnectionCount = new AtomicInteger(0);
        this.qos1Outbound = new QoS1OutboundService(
                qos1RetransmitEnabled,
                qos1RetransmitIntervalMs,
                qos1RetransmitMaxAttempts,
                log,
                retransmitTimer
        );
        this.qos2Outbound = new QoS2OutboundService(
                qos1RetransmitEnabled,
                qos1RetransmitIntervalMs,
                qos1RetransmitMaxAttempts,
                qos1Outbound,
                log,
                retransmitTimer
        );
        LongConsumer pendingDeltaRecorder = delta -> METRIC_QOS2_IN_PENDING.add(delta);
        this.qos2Inbound = new QoS2InboundService(
                this::dispatchInboundPublish,
                pendingDeltaRecorder,
                METRIC_QOS2_IN_COMPLETED_TOTAL::increment,
                inboundQos2PendingMax,
                log
        );
        this.deviceLifecyclePublisher = new DeviceLifecyclePublisher(
                (topic, body) -> publishToSubscribers(topic, body, false, false, 1)
        );
        this.eventNotifyRouter = eventNotifyRouter != null ? eventNotifyRouter
                : new EventNotifyRouter(false, Collections.emptyMap(), Collections.emptyList());
        this.topicRateLimiter = new TopicRateLimiter();
        INSTANCE = this;
    }

    public MqttProtocolHandler(AuthProvider authProvider,
                               AclProvider aclProvider,
                               boolean qos1RetransmitEnabled,
                               long qos1RetransmitIntervalMs,
                               int qos1RetransmitMaxAttempts,
                               int maxConnections,
                               int inboundQos2PendingMax,
                               SessionService SESSION_SERVICE,
                               RetainStore RETAIN_STORE,
                               EventNotifyRouter eventNotifyRouter,
                               TopicRateLimiter topicRateLimiter) {
        this(authProvider, aclProvider, qos1RetransmitEnabled, qos1RetransmitIntervalMs,
                qos1RetransmitMaxAttempts, maxConnections, inboundQos2PendingMax,
                SESSION_SERVICE, RETAIN_STORE, eventNotifyRouter);
    }

    public TopicRateLimiter getTopicRateLimiter() {
        return topicRateLimiter;
    }

    public void setRejectionMessageCollector(RejectionMessageCollector collector) {
        this.rejectionMessageCollector = collector;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        if (maxConnections > 0) {
            int current = activeConnectionCount.incrementAndGet();
            if (current > maxConnections) {
                activeConnectionCount.decrementAndGet();
                log.warn("连接数达到上限，拒绝新连接 maxConnections={} current={} remote={} channelId={}",
                        maxConnections, current - 1, ctx.channel().remoteAddress(), ctx.channel().id().asShortText());
                METRIC_CONNECT_REJECTED_TOTAL.increment();
                ctx.close();
                return;
            }
        } else {
            activeConnectionCount.incrementAndGet();
        }
        ClientSessionContext session = ClientSessionContext.of(ctx);
        // 尽早登记，便于 SUBSCRIBE 前管道其它事件也能关联（当前实现以读事件为主）。
        channels.put(ctx.channel().id(), ctx);
        METRIC_CONNECTIONS_ACTIVE.increment();
        session.disconnectReceived(Boolean.FALSE);
        session.closeReason(CloseReason.IO_EXCEPTION);
        session.lastPacketAtMs(System.currentTimeMillis());
        qos1Outbound.onChannelActive(ctx);
        qos2Outbound.onChannelActive(ctx);
        log.info("MQTT TCP already remote={} channelId={}",
                ctx.channel().remoteAddress(),
                ctx.channel().id().asShortText());
        super.channelActive(ctx);
    }

    /**
     * 每收到一帧完整 MQTT 报文（已由 {@link MqttFrameDecoder} 切帧）。
     * <p>
     * 处理顺序：解析固定头消息类型 → 校验 CONNECT 状态（CONNECT 报文除外）→ 分发到各 handle 方法。
     * </p>
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf frame) {
        TraceContext.initTraceId(ctx);
        try {
        ClientSessionContext session = ClientSessionContext.of(ctx);
        // EmbeddedChannel 等场景下 channelActive 顺序可能与真机略有差异，此处幂等登记，避免首包时 channels 未就绪。
        channels.put(ctx.channel().id(), ctx);
        session.lastPacketAtMs(System.currentTimeMillis());
        if (!frame.isReadable()) {
            session.closeReason(CloseReason.PROTOCOL_ERROR);
            closeWithReason(ctx, "empty frame");
            return;
        }
        int fixedHeader = frame.readUnsignedByte();
        int messageType = (fixedHeader >> 4) & 0x0F;
        int flags = fixedHeader & 0x0F;
        int remainingLength = readRemainingLength(frame);
        if (remainingLength < 0) {
            session.closeReason(CloseReason.PROTOCOL_ERROR);
            closeWithReason(ctx, "Remaining Length 解码失败");
            return;
        }
        if (frame.readableBytes() < remainingLength) {
            session.closeReason(CloseReason.PROTOCOL_ERROR);
            closeWithReason(ctx, "剩余长度与缓冲区不符 remaining=" + remainingLength);
            return;
        }

        if (log.isDebugEnabled()) {
            log.debug("MQTT 收包 channelId={} type={} flags=0x{} remaining={}",
                    ctx.channel().id().asShortText(),
                    messageType,
                    Integer.toHexString(flags),
                    remainingLength);
        }

        // CONNECT 之前不允许其它报文（协议要求先完成会话建立）。
        if (messageType != 1 && !Boolean.TRUE.equals(session.connected())) {
            session.closeReason(CloseReason.PROTOCOL_ERROR);
            closeWithReason(ctx, "未 CONNECT 收到报文 type=" + messageType);
            return;
        }

        switch (messageType) {
            case 1: // CONNECT
                handleConnect(ctx, frame, remainingLength);
                break;
            case 3: // PUBLISH
                handlePublish(ctx, flags, frame, remainingLength);
                break;
            case 4: // PUBACK
                handlePubAck(ctx, frame, remainingLength);
                break;
            case 5: // PUBREC (QoS2下行)
                handlePubRecDownstream(ctx, frame, remainingLength);
                break;
            case 7: // PUBCOMP (QoS2下行)
                handlePubCompDownstream(ctx, frame, remainingLength);
                break;
            case 6: // PUBREL
                handlePubRel(ctx, flags, frame, remainingLength);
                break;
            case 8: // SUBSCRIBE
                handleSubscribe(ctx, flags, frame, remainingLength);
                break;
            case 10: // UNSUBSCRIBE
                handleUnsubscribe(ctx, flags, frame, remainingLength);
                break;
            case 12: // PINGREQ
                writePingResp(ctx);
                log.trace("已响应 PINGREQ channelId={}", ctx.channel().id().asShortText());
                break;
            case 14: // DISCONNECT
                session.disconnectReceived(Boolean.TRUE);
                session.closeReason(CloseReason.CLIENT_DISCONNECT);
                closeWithReason(ctx, CloseReason.CLIENT_DISCONNECT, "收到 DISCONNECT");
                break;
            default:
                session.closeReason(CloseReason.PROTOCOL_ERROR);
                // 未支持类型直接关闭，避免半实现语义与现网不一致且难以排查。
                closeWithReason(ctx, "未支持的消息类型 type=" + messageType);
                break;
        }
        } finally {
            TraceContext.clearTraceId();
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        TraceContext.initTraceId(ctx);
        try {
        ClientSessionContext session = ClientSessionContext.of(ctx);
        log.info("MQTT 连接已释放 channelId={} remote={}",
                ctx.channel().id().asShortText(),
                ctx.channel().remoteAddress());

        // Will 发布：仅在“已 CONNECT + 非正常断开（未收到 DISCONNECT）”时触发。
        // 遵循 MQTT 3.1.1 规范：主动 DISCONNECT、踢旧连接、服务端 Shutdown 不发布遗嘱。
        CloseReason closeReason = session.closeReason();
        boolean shouldPublishWill = closeReason != null && closeReason.shouldPublishWill();
        if (Boolean.TRUE.equals(session.connected()) && shouldPublishWill) {
            String willTopic = session.willTopic();
            byte[] willPayload = session.willPayload();
            Integer willQos = session.willQos();
            Boolean willRetain = session.willRetain();
            if (willTopic != null && willPayload != null && willQos != null && willRetain != null) {
                if (!aclAllowsPublish(willTopic, ctx)) {
                    log.warn("Will 被 ACL 拒绝 topic={} channelId={}", willTopic, ctx.channel().id().asShortText());
                } else {
                int delivered = publishToSubscribers(willTopic, willPayload, willRetain, false, willQos);
                if (willRetain) {
                    try {
                        RETAIN_STORE.put(willTopic, willPayload, willQos);
                    } catch (Exception e) {
                        log.warn("Will Retain 存储失败 topic={} error={}", willTopic, e.getMessage());
                    }
                }
                log.info("Will 已发布 topic={} qos={} retain={} bytes={} -> 投递{}路 channelId={}",
                        willTopic, willQos, willRetain, willPayload.length, delivered, ctx.channel().id().asShortText());
                notifyEvent(ctx, EventType.WILL_PUBLISHED, session.clientId(), "will_publish", null);
                }
            }
        }
        if (Boolean.TRUE.equals(session.connected())) {
            String clientId = session.clientId();
            CloseReason reasonEnum = session.closeReason();
            String disconnectReason = reasonEnum != null ? reasonEnum.name() : "connection_lost";
            notifyEvent(ctx, EventType.DISCONNECTED, clientId, disconnectReason, null);
        }

        Boolean clean = session.cleanSession();
        String clientId = session.clientId();
        // 判断当前 channel 是否仍是该 clientId 的活跃连接；若已被新连接接管（重连竞态），
        // 跳过会话级破坏性操作，避免摧毁新连接的会话状态与离线标记（修复 BUG-2）
        boolean stillCurrent = clientId != null
                && ctx.channel().id().equals(CLIENT_TO_CHANNEL.get(clientId));
        if (Boolean.TRUE.equals(clean)) {
            if (stillCurrent) {
                // cleanSession=true：销毁会话，清理订阅索引与 per-channel qosMap
                removeAllSubscriptions(ctx.channel().id());
                SESSION_SERVICE.remove(clientId);
                CLIENT_TO_CHANNEL.remove(clientId);
                notifyEvent(ctx, EventType.SESSION_DESTROYED, clientId, "session_destroy", null);
            }
        } else {
            if (stillCurrent) {
                // cleanSession=false：先将 per-channel 订阅账本拷贝到持久存储（ownedSubscriptionsQos），
                // 再清理订阅索引与 per-channel qosMap（修复 BUG-1：else 分支也要清理索引避免虚增；
                // 注意 unbind 必须在 removeAllSubscriptions 之前，否则 qosMap.clear() 会清空
                // subscriptionsQosRef 指向的同一 map，导致持久订阅丢失）
                SessionService.Session s = SESSION_SERVICE.get(clientId);
                if (s != null) {
                    s.unbindChannelSubscriptions();
                }
                removeAllSubscriptions(ctx.channel().id());
                SESSION_SERVICE.markOffline(clientId);
            } else {
                // 重连竞态：旧 channel 的订阅索引残留也需清理
                removeAllSubscriptions(ctx.channel().id());
            }
        }
        if (channels.remove(ctx.channel().id()) != null) {
            METRIC_CONNECTIONS_ACTIVE.decrement();
            activeConnectionCount.decrementAndGet();
        }
        CHANNEL_TO_CLIENT.remove(ctx.channel().id());
        qos2Inbound.onChannelInactive(ctx);
        qos1Outbound.onChannelInactive(ctx);
        qos2Outbound.onChannelInactive(ctx);
        stopKeepAliveTask(ctx);
        super.channelInactive(ctx);
        } finally {
            TraceContext.clearTraceId();
        }
    }

    /**
     * CONNECT：校验协议名 MQTT、协议级别 4（MQTT 3.1.1）、非空 clientId；
     * 支持 Will（topic/payload/qos/retain）与用户名/密码鉴权（可配置开关）。
     * <p>
     * <b>失败策略</b>：
     * </p>
     * <ul>
     *   <li>协议级别不支持：CONNACK returnCode=0x01（Unacceptable protocol version）并关闭</li>
     *   <li>clientId 为空：CONNACK returnCode=0x02（Identifier rejected）并关闭</li>
     *   <li>鉴权失败：CONNACK returnCode=0x05（Not authorized）并关闭</li>
     *   <li>Will QoS2：本阶段不支持，CONNACK returnCode=0x03（Server unavailable）并关闭</li>
     * </ul>
     */
    private void handleConnect(ChannelHandlerContext ctx, ByteBuf payload, int remainingLength) {
        ClientSessionContext session = ClientSessionContext.of(ctx);
        int start = payload.readerIndex();
        String protocolName = readMqttUtf8(payload);
        if (!"MQTT".equals(protocolName) && !"MQIsdp".equals(protocolName)) {
            log.warn("CONNECT refused reason=protocol_name_invalid protocolName=[{}] channelId={}", protocolName, ctx.channel().id().asShortText());
            notifyEvent(ctx, EventType.CONNECT_REFUSED, null, "protocol_name_invalid", null);
            if (rejectionMessageCollector != null) {
                rejectionMessageCollector.collectConnectRefused(null, RejectionReason.PROTOCOL_NAME_INVALID, 0x01, String.valueOf(ctx.channel().remoteAddress()));
            }
            writeConnAckAndClose(ctx, 0x01);
            METRIC_CONNECT_REJECTED_TOTAL.increment();
            return;
        }
        if (!payload.isReadable(4)) {
            closeWithReason(ctx, "CONNECT 负载过短");
            return;
        }
        int protocolLevel = payload.readUnsignedByte();
        // 先记录协议级别，便于失败时返回正确版本的 CONNACK。
        session.protocolLevel(protocolLevel);
        int connectFlags = payload.readUnsignedByte();
        int keepAlive = payload.readUnsignedShort();

        if (protocolLevel != 0x03 && protocolLevel != 0x04 && protocolLevel != 0x05) {
            log.warn("CONNECT refused reason=unsupported_protocol_level protocolLevel=0x{} channelId={}",
                    Integer.toHexString(protocolLevel), ctx.channel().id().asShortText());
            notifyEvent(ctx, EventType.CONNECT_REFUSED, null, "unsupported_protocol_level", null);
            if (rejectionMessageCollector != null) {
                rejectionMessageCollector.collectConnectRefused(null, RejectionReason.UNSUPPORTED_PROTOCOL_LEVEL, 0x01, String.valueOf(ctx.channel().remoteAddress()));
            }
            writeConnAckAndClose(ctx, 0x01);
            METRIC_CONNECT_REJECTED_TOTAL.increment();
            return;
        }
        // MQTT 5：CONNECT Variable Header 在 KeepAlive 后包含 properties（变长，至少 1 字节）。
        if (protocolLevel == 0x05) {
            if (!skipMqtt5Properties(payload)) {
                closeWithReason(ctx, "CONNECT MQTT5 properties 解析失败");
                return;
            }
        }
        String clientId = readMqttUtf8(payload);
        if (clientId == null || clientId.isEmpty()) {
            log.warn("CONNECT refused reason=client_id_empty channelId={}", ctx.channel().id().asShortText());
            notifyEvent(ctx, EventType.CONNECT_REFUSED, clientId, "client_id_empty", null);
            if (rejectionMessageCollector != null) {
                rejectionMessageCollector.collectConnectRefused(null, RejectionReason.CLIENT_ID_EMPTY, 0x02, String.valueOf(ctx.channel().remoteAddress()));
            }
            writeConnAckAndClose(ctx, 0x02);
            METRIC_CONNECT_REJECTED_TOTAL.increment();
            return;
        }

        // 解析 CONNECT Flags（MQTT 3.1.1）。
        boolean usernameFlag = (connectFlags & 0x80) != 0;
        boolean passwordFlag = (connectFlags & 0x40) != 0;
        boolean willRetain = (connectFlags & 0x20) != 0;
        int willQos = (connectFlags >> 3) & 0x03;
        boolean willFlag = (connectFlags & 0x04) != 0;
        boolean cleanSession = (connectFlags & 0x02) != 0;

        // BUG-8 修复：MQTT-3.1.2-18 — Password Flag=1 但 Username Flag=0 非法
        if (passwordFlag && !usernameFlag) {
            closeWithReason(ctx, "CONNECT Password Flag=1 但 Username Flag=0（违反 MQTT-3.1.2-18）");
            return;
        }

        if (!willFlag && (willRetain || willQos != 0)) {
            closeWithReason(ctx, "CONNECT Will flags 非法(WillFlag=0 但 retain/qos 非 0)");
            return;
        }
        if (willFlag && willQos == 2) {
            log.warn("CONNECT refused reason=will_qos2_unsupported clientId={} channelId={}",
                    clientId, ctx.channel().id().asShortText());
            notifyEvent(ctx, EventType.CONNECT_REFUSED, clientId, "will_qos2_unsupported", null);
            if (rejectionMessageCollector != null) {
                rejectionMessageCollector.collectConnectRefused(clientId, RejectionReason.WILL_QOS2_UNSUPPORTED, 0x03, String.valueOf(ctx.channel().remoteAddress()));
            }
            writeConnAckAndClose(ctx, 0x03);
            METRIC_CONNECT_REJECTED_TOTAL.increment();
            return;
        }

        // CONNECT Payload 顺序：ClientId + (WillProperties + WillTopic + WillPayload) + (UserName) + (Password)
        if (willFlag) {
            // MQTT 5：Will Properties 位于 Will Topic 之前
            if (protocolLevel == 0x05) {
                if (!skipMqtt5Properties(payload)) {
                    closeWithReason(ctx, "CONNECT Will properties 解析失败");
                    return;
                }
            }
            String willTopic = readMqttUtf8(payload);
            byte[] willPayload = readMqttBytes(payload);
            if (willTopic == null || willPayload == null || willTopic.isEmpty()) {
                closeWithReason(ctx, "CONNECT Will 负载解析失败");
                return;
            }
            if (!TopicFilterSupport.isExactTopic(willTopic)) {
                // Will topic 先按“精确主题”约束，后续做通配符/共享订阅时再放开规则。
                closeWithReason(ctx, "CONNECT Will topic 非法或非精确 topic=" + willTopic);
                return;
            }
            session.willTopic(willTopic);
            session.willPayload(willPayload);
            session.willQos(willQos);
            session.willRetain(willRetain);
        }

        String username = null;
        String password = null;
        if (usernameFlag) {
            username = readMqttUtf8(payload);
            if (username == null) {
                closeWithReason(ctx, "CONNECT username 解析失败");
                return;
            }
        }
        if (passwordFlag) {
            password = readMqttUtf8(payload);
            if (password == null) {
                closeWithReason(ctx, "CONNECT password 解析失败");
                return;
            }
        }

        boolean authOk = authProvider.authenticate(clientId, username, password);
        if (!authOk) {
            session.closeReason(CloseReason.AUTH_FAILED);
            log.warn("CONNECT refused reason=auth_failed clientId={} username={} channelId={} remote={}",
                    clientId, username, ctx.channel().id().asShortText(), ctx.channel().remoteAddress());
            notifyEvent(ctx, EventType.CONNECT_REFUSED, clientId, "auth_failed", null);
            if (rejectionMessageCollector != null) {
                rejectionMessageCollector.collectConnectRefused(clientId, RejectionReason.AUTH_FAILED, 0x05, String.valueOf(ctx.channel().remoteAddress()));
            }
            writeConnAckAndClose(ctx, 0x05);
            METRIC_CONNECT_REJECTED_TOTAL.increment();
            return;
        }
        log.info("CONNECT 鉴权通过 clientId={} username={} channelId={}",
                clientId, username, ctx.channel().id().asShortText());

        // 会话：cleanSession=false 时保留订阅与离线队列；重连时恢复订阅并补发离线消息。
        session.clientId(clientId);
        session.cleanSession(cleanSession);
        session.protocolLevel(protocolLevel);
        session.keepAliveSeconds(keepAlive);
        startKeepAliveTaskIfNeeded(ctx, keepAlive);
        CHANNEL_TO_CLIENT.put(ctx.channel().id(), clientId);

        ChannelId oldChannelId = CLIENT_TO_CHANNEL.put(clientId, ctx.channel().id());
        if (oldChannelId != null && !oldChannelId.equals(ctx.channel().id())) {
            CHANNEL_TO_CLIENT.remove(oldChannelId);
            ChannelHandlerContext oldCtx = channels.get(oldChannelId);
            if (oldCtx != null && oldCtx.channel().isActive()) {
                log.warn("clientId={} 已存在旧连接，关闭旧连接 oldChannelId={} newChannelId={}",
                        clientId, oldChannelId.asShortText(), ctx.channel().id().asShortText());
                ClientSessionContext.of(oldCtx).closeReason(CloseReason.KICKED_BY_NEW_CONNECTION);
                notifyEvent(oldCtx, EventType.CONNECTION_KICKED, clientId, "kicked_by_new_connection", null);
                oldCtx.close();
            }
        }

        SessionService.Session persistedSession = SESSION_SERVICE.getOrCreate(clientId);
        SESSION_SERVICE.markOnline(clientId);
        notifyEvent(ctx, EventType.SESSION_CREATED, clientId, "session_create", null);
        // BUG-10 修复：sessionPresent 应反映会话状态存在性（订阅或离线队列非空），
        // 而非仅检查订阅非空（MQTT 3.1.1 §3.1.4）
        boolean sessionPresent = !cleanSession && SESSION_SERVICE.get(clientId) != null
                && (!persistedSession.subscriptionsQos().isEmpty() || !persistedSession.offlineQueue.isEmpty());
        if (cleanSession) {
            persistedSession.subscriptionsQos().clear();
            persistedSession.offlineQueue.clear();
            SESSION_SERVICE.persist(clientId);
        } else {
            for (Map.Entry<String, Integer> e : persistedSession.subscriptionsQos().entrySet()) {
                addSubscription(ctx.channel().id(), e.getKey(), e.getValue() == null ? 0 : e.getValue());
            }
            if (!persistedSession.subscriptionsQos().isEmpty()) {
                sessionPresent = true;
            }
        }

        int consumed = payload.readerIndex() - start;
        if (consumed > remainingLength) {
            closeWithReason(ctx, "CONNECT 已消费长度超过 Remaining Length");
            return;
        }
        session.connected(Boolean.TRUE);
        ByteBuf connAck;
        if (protocolLevel == 0x05) {
            connAck = Unpooled.buffer(5)
                    .writeByte(0x20)
                    .writeByte(0x03)
                    .writeByte(sessionPresent ? 0x01 : 0x00)
                    .writeByte(0x00)
                    .writeByte(0x00);
        } else {
            connAck = Unpooled.buffer(4)
                    .writeByte(0x20)
                    .writeByte(0x02)
                    .writeByte(sessionPresent ? 0x01 : 0x00)
                    .writeByte(0x00);
        }
        ctx.writeAndFlush(connAck);
        log.info("CONNECT 成功 clientId={} keepAlive={}s channelId={}",
                clientId, keepAlive, ctx.channel().id().asShortText());
        METRIC_CONNECT_ACCEPTED_TOTAL.increment();
        notifyEvent(ctx, EventType.CONNECTED, clientId, "connect", null);

        if (!cleanSession) {
            // 补发离线队列（阶段 1：仅内存队列；QoS 取原入队 QoS 与订阅 QoS 的 min）
            int drained = 0;
            SessionService.QueuedMessage qm;
            while ((qm = persistedSession.offlineQueue.poll()) != null) {
                // 以“下行投递”的形式写回该连接
                int subQos = qosForSubscription(ctx.channel().id(), qm.topic);
                int eff = Math.min(qm.qos, subQos);
                if (eff == 0) {
                    byte[] topicBytes = qm.topic.getBytes(StandardCharsets.UTF_8);
                    int rl = 2 + topicBytes.length + qm.payload.length;
                    ByteBuf out = Unpooled.buffer();
                    int fh = 0x30 | (qm.retain ? 0x01 : 0x00);
                    out.writeByte(fh);
                    writeRemainingLength(out, rl);
                    out.writeShort(topicBytes.length);
                    out.writeBytes(topicBytes);
                    out.writeBytes(qm.payload);
                    ctx.writeAndFlush(out);
                } else {
                    int outPacketId = qos1Outbound.nextPacketId(ctx);
                    qos1Outbound.track(ctx, outPacketId, qm.topic, qm.payload, qm.retain);
                    qos1Outbound.sendPublish(ctx, qm.topic, outPacketId, qm.payload, qm.retain, false);
                }
                drained++;
            }
            if (drained > 0) {
                log.info("Session 离线消息已补发 clientId={} drained={} channelId={}",
                        clientId, drained, ctx.channel().id().asShortText());
                SESSION_SERVICE.persist(clientId);
            }
        }
    }

    private static byte[] readMqttBytes(ByteBuf payload) {
        if (!payload.isReadable(2)) {
            return null;
        }
        int len = payload.readUnsignedShort();
        if (!payload.isReadable(len)) {
            return null;
        }
        byte[] bytes = new byte[len];
        payload.readBytes(bytes);
        return bytes;
    }

    /**
     * SUBSCRIBE：要求 flags=0x02（MQTT 3.1.1）；逐项解析 topic + 请求 QoS。
     * topic filter 非法、QoS 非法或 ACL 拒绝时该条目返回码 0x80（Failure）。
     */
    private void handleSubscribe(ChannelHandlerContext ctx, int flags, ByteBuf payload, int remainingLength) {
        ClientSessionContext session = ClientSessionContext.of(ctx);
        if (flags != 0x02 || remainingLength < 3 || !payload.isReadable(2)) {
            closeWithReason(ctx, "SUBSCRIBE 固定头或长度非法 flags=0x" + Integer.toHexString(flags));
            return;
        }
        int packetId = payload.readUnsignedShort();
        Integer level = session.protocolLevel();
        int consumed = 2;
        if (level != null && level == 0x05) {
            int before = payload.readerIndex();
            if (!skipMqtt5Properties(payload)) {
                closeWithReason(ctx, "SUBSCRIBE MQTT5 properties 解析失败");
                return;
            }
            consumed += payload.readerIndex() - before;
        }
        List<Integer> returnCodes = new ArrayList<>();
        List<String> acceptedFilters = new ArrayList<>();

        while (consumed < remainingLength) {
            int itemStart = payload.readerIndex();
            String topicFilter = readMqttUtf8(payload);
            if (topicFilter == null || !payload.isReadable(1)) {
                closeWithReason(ctx, "SUBSCRIBE 项解析失败");
                return;
            }
            int requestedQos = payload.readUnsignedByte() & 0x03;
            consumed += payload.readerIndex() - itemStart;

            boolean valid = TopicFilterSupport.isShareSubscription(topicFilter)
                    ? TopicFilterSupport.parseShareSubscription(topicFilter) != null
                    : TopicFilterSupport.isValidTopicFilter(topicFilter);

            if (requestedQos <= 2 && valid && aclAllowsSubscribe(topicFilter, ctx)) {
                int grantedQos = requestedQos;
                addSubscription(ctx.channel().id(), topicFilter, grantedQos);
                returnCodes.add(grantedQos);
                acceptedFilters.add(topicFilter);
                log.debug("SUBSCRIBE 接受 topic={} requestedQos={} grantedQos={} channelId={}",
                        topicFilter, requestedQos, grantedQos, ctx.channel().id().asShortText());
            } else {
                returnCodes.add(0x80);
                log.debug("SUBSCRIBE 拒绝 topic={} qos={}（filter/QoS/ACL） channelId={}",
                        topicFilter, requestedQos, ctx.channel().id().asShortText());
            }
        }

        Integer protocolLevel = session.protocolLevel();
        boolean mqtt5 = protocolLevel != null && protocolLevel == 0x05;
        int remaining = (mqtt5 ? (2 + 1) : 2) + returnCodes.size();
        ByteBuf subAck = Unpooled.buffer(2 + 2 + returnCodes.size() + (mqtt5 ? 1 : 0));
        subAck.writeByte(0x90);
        writeRemainingLength(subAck, remaining);
        subAck.writeShort(packetId);
        if (mqtt5) {
            subAck.writeByte(0x00); // properties length
        }
        for (Integer code : returnCodes) {
            subAck.writeByte(code);
        }
        ctx.writeAndFlush(subAck);
        for (String acceptedFilter : acceptedFilters) {
            replayRetainedMessages(ctx, acceptedFilter);
        }
        log.info("SUBACK packetId={} returnCodes={} channelId={}",
                packetId, returnCodes, ctx.channel().id().asShortText());
        if (!acceptedFilters.isEmpty()) {
            notifyEvent(ctx, EventType.SUBSCRIBED, session.clientId(), "subscribe", null);
        }
    }

    /**
     * UNSUBSCRIBE：要求 flags=0x02（MQTT 3.1.1）；逐项解析 topicFilter 并从订阅表移除。
     * <p>
     * <b>失败策略</b>：报文结构非法直接关闭连接；取消不存在的订阅视为幂等成功。
     * </p>
     */
    private void handleUnsubscribe(ChannelHandlerContext ctx, int flags, ByteBuf payload, int remainingLength) {
        ClientSessionContext session = ClientSessionContext.of(ctx);
        if (flags != 0x02 || remainingLength < 4 || !payload.isReadable(2)) {
            closeWithReason(ctx, "UNSUBSCRIBE 固定头或长度非法 flags=0x" + Integer.toHexString(flags));
            return;
        }
        int packetId = payload.readUnsignedShort();
        Integer level = session.protocolLevel();
        int consumed = 2;
        if (level != null && level == 0x05) {
            int before = payload.readerIndex();
            if (!skipMqtt5Properties(payload)) {
                closeWithReason(ctx, "UNSUBSCRIBE MQTT5 properties 解析失败");
                return;
            }
            consumed += payload.readerIndex() - before;
        }
        int removed = 0;
        while (consumed < remainingLength) {
            int itemStart = payload.readerIndex();
            String topicFilter = readMqttUtf8(payload);
            if (topicFilter == null) {
                closeWithReason(ctx, "UNSUBSCRIBE topicFilter 解析失败");
                return;
            }
            consumed += payload.readerIndex() - itemStart;
            if (consumed > remainingLength) {
                closeWithReason(ctx, "UNSUBSCRIBE 已消费长度超过 Remaining Length");
                return;
            }
            if (removeSubscription(ctx.channel().id(), topicFilter)) {
                removed++;
            }
        }

        boolean mqtt5 = level != null && level == 0x05;
        ByteBuf unSubAck = Unpooled.buffer(mqtt5 ? 5 : 4);
        unSubAck.writeByte(0xB0);
        unSubAck.writeByte(mqtt5 ? 0x03 : 0x02);
        unSubAck.writeShort(packetId);
        if (mqtt5) {
            unSubAck.writeByte(0x00); // properties length
        }
        ctx.writeAndFlush(unSubAck);
        log.info("UNSUBACK packetId={} removed={} channelId={}",
                packetId, removed, ctx.channel().id().asShortText());
        if (removed > 0) {
            notifyEvent(ctx, EventType.UNSUBSCRIBED, session.clientId(), "unsubscribe", null);
        }
    }

    /**
     * PUBLISH：解析 QoS、Topic、（QoS&gt;0 时）Packet Identifier、应用负载；向匹配订阅者分发。
     * 投递 QoS = min(发布 QoS, 各订阅的授予 QoS)。上行 QoS1 必须回 PUBACK。
     */
    private void handlePublish(ChannelHandlerContext ctx, int flags, ByteBuf payload, int remainingLength) {
        ClientSessionContext session = ClientSessionContext.of(ctx);
        int qos = (flags >> 1) & 0x03;
        boolean dup = (flags & 0x08) != 0;
        boolean retain = (flags & 0x01) == 0x01;
        int start = payload.readerIndex();
        String topic = readMqttUtf8(payload);
        if (!TopicFilterSupport.isExactTopic(topic)) {
            log.debug("忽略非精确主题或非法 topic=[{}] channelId={} remote={}",
                    topic,
                    ctx.channel().id().asShortText(),
                    ctx.channel().remoteAddress());
            return;
        }
        if (!aclAllowsPublish(topic, ctx)) {
            // PUBLISH 拒绝策略：直接关闭连接（与很多 Broker 的“协议违规/未授权”处理一致，避免继续收消息）。
            closeWithReason(ctx, "ACL 拒绝 PUBLISH topic=" + topic);
            return;
        }
        if (topicRateLimiter != null && topicRateLimiter.hasLimit(topic)) {
            if (!topicRateLimiter.tryAcquire(topic)) {
                String rlStrategy = topicRateLimiter.getStrategy(topic);
                com.mars.linker.broker.netty.trace.StructuredLogger.warn(
                        "RATE_LIMIT", session.clientId(), "topic_rate_limited",
                        0, "REJECTED", "topic=" + topic + " strategy=" + rlStrategy);
                if ("disconnect".equals(rlStrategy)) {
                    closeWithReason(ctx, CloseReason.TOPIC_RATE_LIMITED,
                            "主题速率超限 topic=" + topic);
                    return;
                }
                return;
            }
        }
        if (topic.startsWith(DELAYED_TOPIC_PREFIX) && parseDelayedTopic(topic) == null) {
            closeWithReason(ctx, "DELAYED topic 非法 topic=" + topic);
            return;
        }
        int packetId = 0;
        if (qos > 0) {
            if (!payload.isReadable(2)) {
                closeWithReason(ctx, "PUBLISH QoS>0 缺少 Packet Identifier");
                return;
            }
            packetId = payload.readUnsignedShort();
        }
        Integer level = session.protocolLevel();
        if (level != null && level == 0x05) {
            if (!skipMqtt5Properties(payload)) {
                closeWithReason(ctx, "PUBLISH MQTT5 properties 解析失败");
                return;
            }
        }
        int consumed = payload.readerIndex() - start;
        int payloadBytes = remainingLength - consumed;
        if (payloadBytes < 0 || !payload.isReadable(payloadBytes)) {
            closeWithReason(ctx, "PUBLISH 应用负载长度非法");
            return;
        }
        byte[] msgBytes = new byte[payloadBytes];
        payload.readBytes(msgBytes);
        if (qos == 2) {
            qos2Inbound.onInboundQos2Publish(ctx, packetId, topic, msgBytes, retain, dup, qos);
            return;
        }
        int delivered = dispatchInboundPublish(ctx, topic, msgBytes, retain, qos > 0 && dup, qos);
        if (qos == 1) {
            writePubAck(ctx, packetId);
        }
        log.info("PUBLISH topic={} pubQos={} dup={} retain={} bytes={} -> 投递{}路 channelId={}",
                topic, qos, dup, retain, msgBytes.length, delivered, ctx.channel().id().asShortText());
    }

    /**
     * PUBACK：客户端对<b>本 Broker 下发的 QoS1 PUBLISH</b> 的确认；从 inflight 集合移除 packetId。
     */
    private void handlePubAck(ChannelHandlerContext ctx, ByteBuf payload, int remainingLength) {
        if (remainingLength != 2 || !payload.isReadable(2)) {
            closeWithReason(ctx, "PUBACK 长度必须为 2");
            return;
        }
        int packetId = payload.readUnsignedShort();
        qos1Outbound.onPubAck(ctx, packetId);
        log.debug("PUBACK packetId={} channelId={}", packetId, ctx.channel().id().asShortText());
    }

    private void handlePubRecDownstream(ChannelHandlerContext ctx, ByteBuf payload, int remainingLength) {
        if (remainingLength < 2 || !payload.isReadable(2)) {
            closeWithReason(ctx, "PUBREC 长度非法");
            return;
        }
        int packetId = payload.readUnsignedShort();
        qos2Outbound.onPubRec(ctx, packetId);
        log.debug("PUBREC(下行) packetId={} channelId={}", packetId, ctx.channel().id().asShortText());
    }

    private void handlePubCompDownstream(ChannelHandlerContext ctx, ByteBuf payload, int remainingLength) {
        if (remainingLength != 2 || !payload.isReadable(2)) {
            closeWithReason(ctx, "PUBCOMP 长度非法");
            return;
        }
        int packetId = payload.readUnsignedShort();
        qos2Outbound.onPubComp(ctx, packetId);
        log.debug("PUBCOMP(下行) packetId={} channelId={}", packetId, ctx.channel().id().asShortText());
    }

    private void handlePubRel(ChannelHandlerContext ctx, int flags, ByteBuf payload, int remainingLength) {
        if (flags != 0x02 || remainingLength != 2 || !payload.isReadable(2)) {
            closeWithReason(ctx, "PUBREL 固定头或长度非法");
            return;
        }
        int packetId = payload.readUnsignedShort();
        qos2Inbound.onPubRel(ctx, packetId);
    }

    private int dispatchInboundPublish(ChannelHandlerContext ctx,
                                       String topic,
                                       byte[] payload,
                                       boolean retain,
                                       boolean dup,
                                       int qos) {
        DelayedTarget delayed = parseDelayedTopic(topic);
        if (delayed != null) {
            long delaySeconds = delayed.delaySeconds;
            String targetTopic = delayed.targetTopic;
            byte[] copy = new byte[payload.length];
            System.arraycopy(payload, 0, copy, 0, payload.length);
            ctx.executor().schedule(() -> {
                int delivered = publishToSubscribers(targetTopic, copy, retain, dup, qos);
                METRIC_PUBLISH_IN_TOTAL.increment();
                METRIC_PUBLISH_OUT_TOTAL.add(delivered);
                processRetainStore(targetTopic, copy, qos, retain);
                log.debug("DELAYED PUBLISH 已投递 sourceTopic={} targetTopic={} delay={}s delivered={}",
                        topic, targetTopic, delaySeconds, delivered);
            }, delaySeconds, TimeUnit.SECONDS);
            return 0;
        }
        int delivered = publishToSubscribers(topic, payload, retain, dup, qos);
        METRIC_PUBLISH_IN_TOTAL.increment();
        METRIC_PUBLISH_OUT_TOTAL.add(delivered);
        processRetainStore(topic, payload, qos, retain);
        return delivered;
    }

    private static DelayedTarget parseDelayedTopic(String topic) {
        if (topic == null || !topic.startsWith(DELAYED_TOPIC_PREFIX)) {
            return null;
        }
        String remain = topic.substring(DELAYED_TOPIC_PREFIX.length());
        int slash = remain.indexOf('/');
        if (slash <= 0 || slash == remain.length() - 1) {
            return null;
        }
        String secondsPart = remain.substring(0, slash);
        String targetTopic = remain.substring(slash + 1);
        long seconds;
        try {
            seconds = Long.parseLong(secondsPart);
        } catch (NumberFormatException e) {
            return null;
        }
        if (seconds < 0 || !TopicFilterSupport.isExactTopic(targetTopic)) {
            return null;
        }
        return new DelayedTarget(seconds, targetTopic);
    }

    private static final class DelayedTarget {
        final long delaySeconds;
        final String targetTopic;

        private DelayedTarget(long delaySeconds, String targetTopic) {
            this.delaySeconds = delaySeconds;
            this.targetTopic = targetTopic;
        }
    }

    /**
     * @return 实际尝试写出的订阅者路数（含写失败时仍计数尝试，当前未对 write 失败单独统计）
     */
    private int publishToSubscribers(String topic, byte[] payload, boolean retain, boolean dup, int pubQos) {
        return PublishRouter.publishAndEnqueueOffline(
                topic,
                payload,
                retain,
                dup,
                pubQos,
                SUBSCRIPTION_REGISTRY,
                channels,
                SESSION_SERVICE,
                CLIENT_TO_CHANNEL,
                this::qosForSubscription,
                qos1Outbound::nextPacketId,
                qos1Outbound::track,
                qos2Outbound::publishQos2,
                log
        );
    }

    private boolean aclAllowsSubscribe(String topicFilter, ChannelHandlerContext ctx) {
        boolean allowed = aclProvider.allowsSubscribe(topicFilter);
        if (!allowed) {
            METRIC_ACL_SUB_DENY_TOTAL.increment();
            String clientId = CHANNEL_TO_CLIENT.get(ctx.channel().id());
            log.warn("ACL denied action=subscribe clientId={} topicFilter={} channelId={}",
                    clientId, topicFilter, ctx.channel().id().asShortText());
            if (rejectionMessageCollector != null) {
                rejectionMessageCollector.collectAclSubscribeDenied(clientId, topicFilter, String.valueOf(ctx.channel().remoteAddress()));
            }
        }
        return allowed;
    }

    private boolean aclAllowsPublish(String topic, ChannelHandlerContext ctx) {
        boolean allowed = aclProvider.allowsPublish(topic);
        if (!allowed) {
            METRIC_ACL_PUB_DENY_TOTAL.increment();
            String clientId = CHANNEL_TO_CLIENT.get(ctx.channel().id());
            log.warn("ACL denied action=publish clientId={} topic={} channelId={}",
                    clientId, topic, ctx.channel().id().asShortText());
            if (rejectionMessageCollector != null) {
                rejectionMessageCollector.collectAclPublishDenied(clientId, topic, String.valueOf(ctx.channel().remoteAddress()));
            }
        }
        return allowed;
    }

    private int qosForSubscription(ChannelId subscriberId, String topicOrFilter) {
        ChannelHandlerContext c = channels.get(subscriberId);
        if (c == null) {
            return 0;
        }
        ConcurrentHashMap<String, Integer> m = c.channel().attr(SUBSCRIPTION_QOS).get();
        if (m == null) {
            return 0;
        }
        return m.getOrDefault(topicOrFilter, 0);
    }

    private void startKeepAliveTaskIfNeeded(ChannelHandlerContext ctx, int keepAliveSeconds) {
        ClientSessionContext session = ClientSessionContext.of(ctx);
        if (keepAliveSeconds <= 0) {
            return;
        }
        if (session.keepAliveTask() != null) {
            return;
        }
        long checkIntervalMs = Math.max(1_000L, Math.min(5_000L, keepAliveSeconds * 500L));
        Timeout t = keepAliveTimer.newTimeout(
                timeout -> {
                    if (timeout.isExpired()) {
                        checkKeepAliveTimeout(ctx, keepAliveSeconds);
                    }
                    if (ctx.channel().isActive()) {
                        startKeepAliveTaskIfNeeded(ctx, keepAliveSeconds);
                    }
                },
                checkIntervalMs, TimeUnit.MILLISECONDS
        );
        session.keepAliveTask(t);
    }

    /**
     * 标记服务端正在关闭，所有连接将设置 CloseReason.SERVER_SHUTDOWN，不发布遗嘱消息。
     */
    public void markServerShutdown() {
        serverShuttingDown = true;
        for (ChannelHandlerContext ctx : channels.values()) {
            if (ctx.channel().isActive()) {
                ClientSessionContext.of(ctx).closeReason(CloseReason.SERVER_SHUTDOWN);
            }
        }
        log.info("已标记所有活跃连接为 SERVER_SHUTDOWN，不发布遗嘱消息");
    }

    private void stopKeepAliveTask(ChannelHandlerContext ctx) {
        ClientSessionContext session = ClientSessionContext.of(ctx);
        Timeout t = session.keepAliveTask();
        if (t != null) {
            t.cancel();
            session.keepAliveTask(null);
        }
    }

    private void checkKeepAliveTimeout(ChannelHandlerContext ctx, int keepAliveSeconds) {
        ClientSessionContext session = ClientSessionContext.of(ctx);
        if (!ctx.channel().isActive()) {
            return;
        }
        Long last = session.lastPacketAtMs();
        if (last == null) {
            return;
        }
        long now = System.currentTimeMillis();
        long idleMs = now - last;
        long timeoutMs = keepAliveSeconds * 1500L;
        if (idleMs > timeoutMs) {
            session.closeReason(CloseReason.KEEPALIVE_TIMEOUT);
            closeWithReason(ctx, CloseReason.KEEPALIVE_TIMEOUT, "keepAlive 超时 idleMs=" + idleMs + " timeoutMs=" + timeoutMs);
        }
    }

    private static boolean skipMqtt5Properties(ByteBuf payload) {
        int len = readVariableByteInt(payload);
        if (len < 0) {
            return false;
        }
        if (!payload.isReadable(len)) {
            return false;
        }
        payload.skipBytes(len);
        return true;
    }

    /**
     * MQTT 5 变长整型（Variable Byte Integer，1~4 字节）。失败返回 -1。
     */
    private static int readVariableByteInt(ByteBuf buf) {
        int multiplier = 1;
        int value = 0;
        int loops = 0;
        while (true) {
            if (!buf.isReadable()) {
                return -1;
            }
            int encodedByte = buf.readUnsignedByte();
            value += (encodedByte & 127) * multiplier;
            if ((encodedByte & 128) == 0) {
                return value;
            }
            multiplier *= 128;
            loops++;
            if (loops > 3) {
                return -1;
            }
        }
    }

    private void processRetainStore(String topic, byte[] payload, int qos, boolean retain) {
        if (!retain) {
            return;
        }
        if (payload == null || payload.length == 0) {
            RETAIN_STORE.remove(topic);
            return;
        }
        RETAIN_STORE.put(topic, payload, qos);
    }

    /**
     * 测试辅助：清空内存态路由/会话状态，并删除落盘文件。
     */
    synchronized void resetStateForTests() {
        SUBSCRIPTION_REGISTRY.clear();
        channels.clear();
        activeConnectionCount.set(0);
        SESSION_SERVICE.resetForTests();
        CLIENT_TO_CHANNEL.clear();
        CHANNEL_TO_CLIENT.clear();
        try {
            Files.deleteIfExists(Paths.get("data", "retain-store.tsv"));
        } catch (IOException ignored) {
            // ignore for tests
        }
        resetMetricsForTests();
    }

    synchronized void reloadSessionsForTests() {
        SESSION_SERVICE.reloadForTests();
    }

    static synchronized void resetMetricsForTests() {
        MqttProtocolHandler inst = INSTANCE;
        if (inst == null) return;
        inst.METRIC_CONNECTIONS_ACTIVE.reset();
        inst.METRIC_CONNECT_ACCEPTED_TOTAL.reset();
        inst.METRIC_CONNECT_REJECTED_TOTAL.reset();
        inst.METRIC_PUBLISH_IN_TOTAL.reset();
        inst.METRIC_PUBLISH_OUT_TOTAL.reset();
        inst.METRIC_QOS2_IN_PENDING.reset();
        inst.METRIC_QOS2_IN_COMPLETED_TOTAL.reset();
        inst.METRIC_ACL_SUB_DENY_TOTAL.reset();
        inst.METRIC_ACL_PUB_DENY_TOTAL.reset();
    }

    public static long metricConnectionsActive() {
        MqttProtocolHandler inst = INSTANCE;
        return inst != null ? inst.METRIC_CONNECTIONS_ACTIVE.sum() : 0;
    }

    public static long metricConnectAcceptedTotal() {
        MqttProtocolHandler inst = INSTANCE;
        return inst != null ? inst.METRIC_CONNECT_ACCEPTED_TOTAL.sum() : 0;
    }

    public static long metricConnectRejectedTotal() {
        MqttProtocolHandler inst = INSTANCE;
        return inst != null ? inst.METRIC_CONNECT_REJECTED_TOTAL.sum() : 0;
    }

    public static long metricPublishInTotal() {
        MqttProtocolHandler inst = INSTANCE;
        return inst != null ? inst.METRIC_PUBLISH_IN_TOTAL.sum() : 0;
    }

    public static long metricPublishOutTotal() {
        MqttProtocolHandler inst = INSTANCE;
        return inst != null ? inst.METRIC_PUBLISH_OUT_TOTAL.sum() : 0;
    }

    public static long metricQos2InPending() {
        MqttProtocolHandler inst = INSTANCE;
        return inst != null ? inst.METRIC_QOS2_IN_PENDING.sum() : 0;
    }

    public static long metricQos2InCompletedTotal() {
        MqttProtocolHandler inst = INSTANCE;
        return inst != null ? inst.METRIC_QOS2_IN_COMPLETED_TOTAL.sum() : 0;
    }

    public static long metricAclSubscribeDenyTotal() {
        MqttProtocolHandler inst = INSTANCE;
        return inst != null ? inst.METRIC_ACL_SUB_DENY_TOTAL.sum() : 0;
    }

    public static long metricAclPublishDenyTotal() {
        MqttProtocolHandler inst = INSTANCE;
        return inst != null ? inst.METRIC_ACL_PUB_DENY_TOTAL.sum() : 0;
    }

    public SubscriptionRegistry subscriptionRegistry() {
        return SUBSCRIPTION_REGISTRY;
    }

    public SessionService sessionService() {
        return SESSION_SERVICE;
    }

    public RetainStore retainStore() {
        return RETAIN_STORE;
    }

    public Map<ChannelId, ChannelHandlerContext> channels() { return channels; }
    public Map<String, ChannelId> clientToChannel() { return CLIENT_TO_CHANNEL; }

    public boolean disconnectClient(String clientId, String reason) {
        ChannelId channelId = CLIENT_TO_CHANNEL.get(clientId);
        if (channelId == null) {
            return false;
        }
        ChannelHandlerContext ctx = channels.get(channelId);
        if (ctx == null || !ctx.channel().isActive()) {
            return false;
        }
        ClientSessionContext.of(ctx).closeReason(CloseReason.KICKED_BY_NEW_CONNECTION);
        ctx.close();
        return true;
    }

    private void replayRetainedMessages(ChannelHandlerContext ctx, String topicFilter) {
        String normalizedFilter = topicFilter;
        if (TopicFilterSupport.isShareSubscription(topicFilter)) {
            TopicFilterSupport.ShareSubscription ss = TopicFilterSupport.parseShareSubscription(topicFilter);
            if (ss == null) {
                return;
            }
            normalizedFilter = ss.filter;
        }
        int subQos = qosForSubscription(ctx.channel().id(), topicFilter);
        for (RetainStore.RetainedMessage retained : RETAIN_STORE.list()) {
            if (!TopicFilterSupport.matchTopicFilter(normalizedFilter, retained.topic)) {
                continue;
            }
            int eff = Math.min(subQos, retained.qos);
            writeRetainedToSubscriber(ctx, retained.topic, retained.payload, eff);
        }
    }

    private void writeRetainedToSubscriber(ChannelHandlerContext ctx, String topic, byte[] payload, int qos) {
        byte[] topicBytes = topic.getBytes(StandardCharsets.UTF_8);
        if (qos <= 0) {
            int rl = 2 + topicBytes.length + payload.length;
            ByteBuf out = Unpooled.buffer();
            // retain=1 qos=0
            out.writeByte(0x31);
            writeRemainingLength(out, rl);
            out.writeShort(topicBytes.length);
            out.writeBytes(topicBytes);
            out.writeBytes(payload);
            ctx.writeAndFlush(out);
            return;
        }
        int packetId = qos1Outbound.nextPacketId(ctx);
        qos1Outbound.track(ctx, packetId, topic, payload, true);
        qos1Outbound.sendPublish(ctx, topic, packetId, payload, true, false);
    }

    private void writePubAck(ChannelHandlerContext ctx, int packetId) {
        ByteBuf buf = Unpooled.buffer(4);
        buf.writeByte(0x40);
        buf.writeByte(0x02);
        buf.writeShort(packetId);
        ctx.writeAndFlush(buf);
        log.debug("上行 PUBACK packetId={} channelId={}", packetId, ctx.channel().id().asShortText());
    }

    private void addSubscription(ChannelId channelId, String topicFilter, int grantedQos) {
        SUBSCRIPTION_REGISTRY.add(channelId, topicFilter);
        ChannelHandlerContext c = channels.get(channelId);
        if (c == null) {
            log.warn("addSubscription 时找不到 ChannelHandlerContext channelId={}", channelId.asShortText());
            return;
        }
        ConcurrentHashMap<String, Integer> qosMap = c.channel().attr(SUBSCRIPTION_QOS).get();
        if (qosMap == null) {
            qosMap = new ConcurrentHashMap<>();
            c.channel().attr(SUBSCRIPTION_QOS).set(qosMap);
        }
        qosMap.put(topicFilter, grantedQos);
        Set<String> myTopics = c.channel().attr(TOPIC_SUBSCRIPTIONS).get();
        if (myTopics == null) {
            myTopics = new CopyOnWriteArraySet<>();
            c.channel().attr(TOPIC_SUBSCRIPTIONS).set(myTopics);
        }
        myTopics.add(topicFilter);
        String clientId = ClientSessionContext.of(c).clientId();
        if (clientId != null) {
            SessionService.Session persistedSession = SESSION_SERVICE.getOrCreate(clientId);
            if (persistedSession.subscriptionsQos() != qosMap) {
                persistedSession.bindChannelSubscriptions(qosMap);
            }
            SESSION_SERVICE.persist(clientId);
        }
    }

    /**
     * 移除单条订阅（精确/通配符/$share）。
     *
     * @return 若确实移除了订阅则为 true，否则为 false（幂等）
     */
    private boolean removeSubscription(ChannelId channelId, String topicFilter) {
        ChannelHandlerContext channelCtx = channels.get(channelId);
        if (channelCtx == null) {
            return false;
        }

        boolean removed = false;
        removed = SUBSCRIPTION_REGISTRY.remove(channelId, topicFilter);

        if (removed) {
            ConcurrentHashMap<String, Integer> qosMap = channelCtx.channel().attr(SUBSCRIPTION_QOS).get();
            if (qosMap != null) {
                qosMap.remove(topicFilter);
            }
            Set<String> myFilters = channelCtx.channel().attr(TOPIC_SUBSCRIPTIONS).get();
            if (myFilters != null) {
                myFilters.remove(topicFilter);
            }
            String clientId = ClientSessionContext.of(channelCtx).clientId();
            if (clientId != null) {
                SESSION_SERVICE.persist(clientId);
            }
        }
        return removed;
    }

    private void removeAllSubscriptions(ChannelId channelId) {
        ChannelHandlerContext channelCtx = channels.get(channelId);
        if (channelCtx == null) {
            return;
        }
        Set<String> filters = channelCtx.channel().attr(TOPIC_SUBSCRIPTIONS).get();
        if (filters == null || filters.isEmpty()) {
            return;
        }
        for (String filter : filters) {
            SUBSCRIPTION_REGISTRY.remove(channelId, filter);
        }
        ConcurrentHashMap<String, Integer> qosMap = channelCtx.channel().attr(SUBSCRIPTION_QOS).get();
        if (qosMap != null) {
            qosMap.clear();
        }
        String clientId = ClientSessionContext.of(channelCtx).clientId();
        if (clientId != null) {
            SESSION_SERVICE.persist(clientId);
        }
        filters.clear();
        log.debug("已清理订阅 channelId={} 主题数已置空", channelId.asShortText());
    }

    private void writeConnAckAndClose(ChannelHandlerContext ctx, int returnCode) {
        ClientSessionContext session = ClientSessionContext.of(ctx);
        if (session.closeReason() == null) {
            session.closeReason(returnCode == 0x05 ? CloseReason.AUTH_FAILED : CloseReason.PROTOCOL_VIOLATION);
        }
        log.warn("CONNECT 拒绝 returnCode=0x{} reason={} channelId={}",
                Integer.toHexString(returnCode & 0xFF), session.closeReason().name(), ctx.channel().id().asShortText());
        Integer level = ClientSessionContext.of(ctx).protocolLevel();
        boolean mqtt5 = level != null && level == 0x05;
        ByteBuf connAck;
        if (mqtt5) {
            // MQTT 5：Reason Code + properties length(0)。这里复用 3.1.1 的拒绝码值，保持联调语义一致。
            connAck = Unpooled.buffer(5)
                    .writeByte(0x20)
                    .writeByte(0x03)
                    .writeByte(0x00)
                    .writeByte(returnCode & 0xFF)
                    .writeByte(0x00);
        } else {
            connAck = Unpooled.buffer(4)
                    .writeByte(0x20)
                    .writeByte(0x02)
                    .writeByte(0x00)
                    .writeByte(returnCode & 0xFF);
        }
        ctx.writeAndFlush(connAck).addListener(f -> ctx.close());
    }

    private void writePingResp(ChannelHandlerContext ctx) {
        ByteBuf pingResp = Unpooled.buffer(2)
                .writeByte(0xD0)
                .writeByte(0x00);
        ctx.writeAndFlush(pingResp);
    }

    private void closeWithReason(ChannelHandlerContext ctx, String reason) {
        ClientSessionContext session = ClientSessionContext.of(ctx);
        if (session.closeReason() == null) {
            session.closeReason(CloseReason.PROTOCOL_ERROR);
        }
        log.warn("关闭连接: {} reason={} remote={} channelId={}",
                reason, session.closeReason().name(), ctx.channel().remoteAddress(), ctx.channel().id().asShortText());
        ctx.close();
    }

    private void closeWithReason(ChannelHandlerContext ctx, CloseReason closeReason, String detail) {
        ClientSessionContext.of(ctx).closeReason(closeReason);
        log.warn("关闭连接: {} reason={} remote={} channelId={}",
                detail, closeReason.name(), ctx.channel().remoteAddress(), ctx.channel().id().asShortText());
        ctx.close();
    }

    /**
     * 从当前 readerIndex 起解析 MQTT Remaining Length（变长编码，最多 4 字节）。
     */
    private static int readRemainingLength(ByteBuf payload) {
        int multiplier = 1;
        int value = 0;
        int loops = 0;
        while (true) {
            if (!payload.isReadable()) {
                return -1;
            }
            int encodedByte = payload.readUnsignedByte();
            value += (encodedByte & 127) * multiplier;
            if ((encodedByte & 128) == 0) {
                return value;
            }
            multiplier *= 128;
            loops++;
            if (loops > 3) {
                return -1;
            }
        }
    }

    private static String readMqttUtf8(ByteBuf payload) {
        if (!payload.isReadable(2)) {
            return null;
        }
        int len = payload.readUnsignedShort();
        if (!payload.isReadable(len)) {
            return null;
        }
        byte[] bytes = new byte[len];
        payload.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void writeRemainingLength(ByteBuf out, int value) {
        int x = value;
        do {
            int encodedByte = x % 128;
            x = x / 128;
            if (x > 0) {
                encodedByte = encodedByte | 0x80;
            }
            out.writeByte(encodedByte);
        } while (x > 0);
    }

    private void notifyEvent(ChannelHandlerContext ctx,
                             EventType eventType,
                             String clientId,
                             String reason,
                             Map<String, Object> extensions) {
        String topic = eventNotifyRouter.shouldNotify(eventType, clientId);
        if (topic == null) {
            return;
        }
        if (!aclAllowsPublish(topic, ctx)) {
            log.warn("事件通知被 ACL 拒绝 topic={} event={} clientId={}", topic, eventType.getValue(), clientId);
            return;
        }
        DeviceLifecyclePublisher.PublishResult result =
                deviceLifecyclePublisher.publish(ctx, topic, eventType.getValue(), clientId, reason, extensions);
        if (result == null) {
            return;
        }
        log.info("事件通知 topic={} event={} clientId={} ip={} port={} reason={} delivered={}",
                topic, eventType.getValue(), clientId, result.ip, result.port, reason, result.delivered);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ClientSessionContext session = ClientSessionContext.of(ctx);
        if (cause instanceof java.io.IOException) {
            session.closeReason(CloseReason.IO_EXCEPTION);
        } else {
            session.closeReason(CloseReason.CHANNEL_EXCEPTION);
        }
        log.warn("Netty exceptionCaught，关闭连接 reason={} channelId={} remote={}",
                session.closeReason().name(),
                ctx.channel().id().asShortText(),
                ctx.channel().remoteAddress(),
                cause);
        ctx.close();
    }
}
