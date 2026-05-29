package com.mars.linker.broker.config;

import com.mars.linker.broker.netty.MqttFrameDecoder;
import com.mars.linker.broker.netty.NettyMqttBrokerServer;
import com.mars.linker.broker.netty.protocol.EventForwardConfig;
import com.mars.linker.broker.netty.protocol.ForwardRule;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 自研 MQTT Broker 配置项（Spring Boot {@link ConfigurationProperties}）。
 * <p>
 * <b>职责边界</b>：仅承载配置，不包含任何业务逻辑；具体使用方见
 * {@link NettyMqttBrokerServer} / {@link MqttFrameDecoder}。
 * </p>
 * <p>
 * <b>测试备注</b>：建议至少覆盖两类验证：
 * </p>
 * <ul>
 *   <li>配置绑定：在集成测试/本地启动时通过 YAML/ENV 覆盖 {@code tcpPort}、{@code maxPacketBytes}，确认生效。</li>
 *   <li>边界行为：{@code maxPacketBytes} 变更后，超限帧应被 {@link MqttFrameDecoder} 关闭连接。</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "mars.linker.broker")
public class MarsLinkerMqttBrokerProperties {

    /**
     * 是否启用 Netty MQTT 接入（默认 false，避免与本机 docker EMQX 端口冲突）。
     */
    private boolean nettyEnabled = false;

    /**
     * Netty MQTT TCP 监听端口（仅在 nettyEnabled=true 时使用）。
     */
    private int tcpPort = 11883;

    /**
     * Boss 线程数。
     */
    private int bossThreads = 1;

    /**
     * Worker 线程数；0 表示使用 CPU×2。
     */
    private int workerThreads = 0;

    /**
     * 单帧最大字节数（含固定头 + Remaining Length 编码 + 负载），超过则断开连接。
     */
    private int maxPacketBytes = 256 * 1024;

    /**
     * TCP 监听 backlog（对应 {@link io.netty.channel.ChannelOption#SO_BACKLOG}）。 
     */
    private int soBacklog = 1024;

    /**
     * Broker 总连接数上限；小于等于 0 表示不限制。
     */
    private int maxConnections = 0;

    /**
     * 持久化存储模式：file（默认）/redis/db。
     */
    private String storageMode = "file";

    /**
     * 是否启用持久化（总开关）。
     * 关闭时：Session/Retain 不会写入 file/redis/db，仅内存运行。
     */
    private boolean storageEnabled = true;

    /**
     * 文件存储：Session 数据文件路径。
     */
    private String sessionStoreFilePath = "data/session-store.tsv";

    /**
     * 文件存储：Retain 数据文件路径。
     */
    private String retainStoreFilePath = "data/retain-store.tsv";

    /**
     * 启动迁移开关：当 mode!=file 且开启时，从 file 存储加载一次数据并写入目标存储（幂等）。
     */
    private boolean storageMigrateOnStartup = false;

    /**
     * Redis 存储配置。
     */
    private String storageRedisAddress;
    private String storageRedisPassword;
    private int storageRedisDatabase = 0;
    private long storageRedisTimeoutMs = 3_000L;
    private String storageRedisKeyPrefix = "ml";

    /**
     * DB 存储配置。
     */
    private String storageDbJdbcUrl;
    private String storageDbUsername;
    private String storageDbPassword;
    private String storageDbSchema = "public";
    private String storageDbTablePrefix = "ml_";

    /** DB 连接池最小空闲连接数。 */
    private int dbPoolMinIdle = 2;

    /** DB 连接池最大连接数。 */
    private int dbPoolMaxSize = 10;

    /** DB 连接池获取连接超时（毫秒）。 */
    private long dbPoolConnectionTimeoutMs = 30_000L;

    /** DB 连接池空闲连接超时（毫秒）。 */
    private long dbPoolIdleTimeoutMs = 600_000L;

    /** DB 连接池连接最大生命周期（毫秒）。 */
    private long dbPoolMaxLifetimeMs = 1_800_000L;

    /** DB 连接池连接泄漏检测阈值（毫秒），0 表示禁用。 */
    private long dbPoolLeakDetectionMs = 60_000L;

    /** 精确主题消息速率限流配置：key=精确主题, value=每秒最大消息数。通配符主题不适用。 */
    private java.util.Map<String, Integer> topicRateLimits = new java.util.LinkedHashMap<>();

    /** 限流超限默认策略：drop=丢弃超限消息, disconnect=断开发布者连接。 */
    private String topicRateLimitDefaultStrategy = "drop";

    /** 每主题限流策略覆盖：key=精确主题, value=策略（drop/disconnect）。 */
    private java.util.Map<String, String> topicRateLimitStrategies = new java.util.LinkedHashMap<>();

    /**
     * 每个 client 持久化离线消息上限；小于等于 0 表示不限制。
     */
    private int sessionOfflineMaxMessages = 10_000;

    /**
     * 离线消息持久化保留时长（毫秒）；小于等于 0 表示不过期。
     */
    private long sessionOfflineTtlMs = 7L * 24 * 60 * 60 * 1000;

    /**
     * retain 消息总量上限；小于等于 0 表示不限制。
     */
    private int retainMaxMessages = 100_000;

    /**
     * retain 消息保留时长（毫秒）；小于等于 0 表示不过期。
     */
    private long retainTtlMs = 30L * 24 * 60 * 60 * 1000;

    /**
     * 是否启用 TLS/MQTTS（默认 false）。
     * <p>
     * 开启后将使用 {@code tlsPort} 监听，并在子通道管道最前面插入 TLS handler。
     * </p>
     */
    private boolean tlsEnabled = false;

    /**
     * TLS/MQTTS 监听端口（仅在 {@code tlsEnabled=true} 时使用）。
     */
    private int tlsPort = 18884;

    /**
     * TLS 证书链文件路径（PEM），如 server.crt。
     */
    private String tlsCertChainPath;

    /**
     * TLS 私钥文件路径（PEM），如 server.key。
     */
    private String tlsPrivateKeyPath;

    /**
     * TLS 私钥口令（可选）。
     */
    private String tlsPrivateKeyPassword;

    /**
     * 是否启用 MQTT CONNECT 用户名/密码鉴权（默认关闭，便于本地联调）。
     * <p>
     * <b>失败策略</b>：开启后，若客户端未携带或校验失败，将返回 CONNACK returnCode=0x05（Not authorized）并关闭连接。
     * </p>
     */
    private boolean authEnabled = false;

    /**
     * 鉴权用户名（仅在 {@code authEnabled=true} 时使用）。
     */
    private String authUsername;

    /**
     * 鉴权密码（仅在 {@code authEnabled=true} 时使用）。
     */
    private String authPassword;

    /**
     * 鉴权模式：{@code static} 使用本配置中的单账号；{@code http} 使用 HTTP 回调
     * （{@code authHttpUrl} 等，见各字段说明）。
     * <p>仅在 {@code authEnabled=true} 时有效；为 {@code false} 时忽略本项。</p>
     */
    private String authMode = "static";

    /**
     * 动态鉴权 HTTP 端点（POST，JSON body）。仅在 {@code authEnabled=true} 且 {@code authMode=http} 时必填。
     * <p>成功条件：返回 2xx；否则判为鉴权失败。</p>
     */
    private String authHttpUrl;

    /**
     * HTTP 鉴权：连接超时（毫秒）。
     */
    private long authHttpConnectTimeoutMs = 2_000L;

    /**
     * HTTP 鉴权：整次请求超时（毫秒，含建连与读响应 body）。
     */
    private long authHttpRequestTimeoutMs = 5_000L;

    /**
     * 可选。若设置则每次回调请求会携带 {@code Authorization} 头（用于保护鉴权服务本身，如 Bearer 令牌）。
     */
    private String authHttpAuthorizationHeader;

    /**
     * 是否启用下行 QoS1 重传（默认关闭，避免阶段 4 初期产生“重复消息”误解）。
     * <p>
     * <b>行为</b>：对下发给订阅者的 QoS1 PUBLISH，在超时未收到 PUBACK 时重发同一 packetId，并置 DUP=1；
     * 达到最大重传次数后停止重传（当前不强制断开连接）。
     * </p>
     */
    private boolean qos1RetransmitEnabled = false;

    /**
     * 下行 QoS1 重传扫描/超时间隔（毫秒）。
     */
    private long qos1RetransmitIntervalMs = 5_000;

    /**
     * 下行 QoS1 最大重传次数（不含首次发送）。
     */
    private int qos1RetransmitMaxAttempts = 3;

    /**
     * 单连接上行 QoS2 pending 窗口上限；小于等于 0 表示不限制。
     */
    private int inboundQos2PendingMax = 1024;

    /**
     * 是否启用 ACL（默认关闭，便于本地联调）。
     * <p>
     * <b>说明</b>：当前 ACL 按“主题前缀”策略工作；可选 HTTP 动态来源，支持热更新。
     * </p>
     */
    private boolean aclEnabled = false;

    /**
     * ACL 模式：{@code static} 使用本配置中的前缀列表；{@code http} 使用 HTTP 拉取规则并定时热更新。
     * <p>仅在 {@code aclEnabled=true} 时有效；为 {@code false} 时忽略本项。</p>
     */
    private String aclMode = "static";

    /**
     * 动态 ACL HTTP 规则源（GET/JSON）。仅在 {@code aclEnabled=true} 且 {@code aclMode=http} 时必填。
     * <p>规则 JSON 结构与 {@link com.mars.linker.broker.netty.acl.HttpAclProvider.AclRuleSet} 一致。</p>
     */
    private String aclHttpUrl;

    /**
     * HTTP ACL：连接超时（毫秒）。
     */
    private long aclHttpConnectTimeoutMs = 2_000L;

    /**
     * HTTP ACL：整次请求超时（毫秒）。
     */
    private long aclHttpRequestTimeoutMs = 5_000L;

    /**
     * HTTP ACL：拉取间隔（毫秒）。仅在 http 模式下使用。
     */
    private long aclHttpRefreshIntervalMs = 10_000L;

    /**
     * 可选。若设置则每次拉取请求会携带 {@code Authorization} 头。
     */
    private String aclHttpAuthorizationHeader;

    /**
     * 允许 SUBSCRIBE 的 topicFilter 前缀（命中任意一个即允许）。为空表示全部允许。
     * <p>
     * 注意：此处按字符串前缀匹配，不做 topic 结构语义解析；建议使用稳定的业务前缀如 {@code dev/}、{@code up/}。
     * </p>
     */
    private List<String> aclAllowSubscribePrefixes = new ArrayList<>();

    /**
     * 允许 PUBLISH 的 topic 前缀（命中任意一个即允许）。为空表示全部允许。
     */
    private List<String> aclAllowPublishPrefixes = new ArrayList<>();

    /**
     * ACL 默认拒绝策略（默认 false）。
     * <p>
     * 为 false 时：allow 列表为空表示“允许全部”；为 true 时：allow 列表为空表示“拒绝全部”。
     * </p>
     */
    private boolean aclDefaultDeny = false;

    /**
     * 明确拒绝 SUBSCRIBE 的 topicFilter 前缀（命中任意一个立即拒绝，优先级高于 allow）。
     */
    private List<String> aclDenySubscribePrefixes = new ArrayList<>();

    /**
     * 明确拒绝 PUBLISH 的 topic 前缀（命中任意一个立即拒绝，优先级高于 allow）。
     */
    private List<String> aclDenyPublishPrefixes = new ArrayList<>();

    /**
     * 是否启用业务链追踪（traceId），默认开启。
     * 关闭时不在日志中输出 traceId，减少开销。
     */
    private boolean traceIdEnabled = true;

    /**
     * 结构化日志输出字段，逗号分隔。
     * 默认输出全部五个字段：traceId,clientId,operation,durationMs,result。
     */
    private String traceIdLogFields = "traceId,clientId,operation,durationMs,result";

    /**
     * 鉴权线程池大小；0 表示使用 CPU×2。
     * 仅在 authMode=http 时生效。
     */
    private int authThreadPoolSize = 0;

    /**
     * 鉴权线程池队列容量。
     * 仅在 authMode=http 时生效；队列满时新鉴权请求快速失败。
     */
    private int authThreadPoolQueueCapacity = 1000;

    /**
     * HTTP 鉴权异步超时（毫秒），超时后返回 CONNACK(0x03 Server Unavailable)。
     * 仅在 authMode=http 时生效。
     */
    private long authHttpTimeoutMs = 5_000L;

    /**
     * 连接数告警阈值比例（0.0-1.0），达到该比例时输出 WARN 日志。
     * 默认 0.8，即达到 maxConnections 的 80% 时告警。
     */
    private double connectionWarnThresholdRatio = 0.8;

    /**
     * 事件通知总开关（默认 false，与标准 MQTT Broker 行为一致）。
     * <p>
     * 关闭时：Broker 不发布任何事件通知消息。
     * 此开关为第一层级控制，各事件类型的 enabled 为第二层级控制。
     * </p>
     */
    private boolean eventNotifyEnabled = false;

    /**
     * 按事件类型配置转发行为。
     * <p>
     * 键名使用 EventType 的 configKey 格式（如 "connected"、"connect-refused"），
     * 支持 Spring Boot 宽松绑定。
     * 未在映射中配置的事件类型默认不启用（enabled=false）且使用默认主题。
     * </p>
     */
    private Map<String, EventForwardConfig> eventNotifyConfig = new LinkedHashMap<>();

    /**
     * 全局默认转发规则列表。
     * <p>
     * 未单独配置 forward-rules 的事件类型继承此全局规则。
     * 默认为空列表，表示未单独配置规则的事件类型对所有设备均匹配（全量转发）。
     * </p>
     */
    private List<ForwardRule> eventNotifyDefaultForwardRules = new ArrayList<>();

    /**
     * 是否启用设备上下线事件通知（旧配置，已废弃，请使用 event-notify-enabled）。
     */
    @Deprecated
    private boolean lifecycleNotifyEnabled = false;

    /**
     * 设备上线事件消息的转发目标 MQTT 主题（旧配置，已废弃）。
     */
    @Deprecated
    private String lifecycleConnectedTopic = "devices/connected";

    /**
     * 设备离线事件消息的转发目标 MQTT 主题（旧配置，已废弃）。
     */
    @Deprecated
    private String lifecycleOfflineTopic = "devices/offline";

    /**
     * 上下线事件转发规则列表（旧配置，已废弃）。
     */
    @Deprecated
    private List<ForwardRule> lifecycleForwardRules = new ArrayList<>();

    public boolean isNettyEnabled() {
        return nettyEnabled;
    }

    public void setNettyEnabled(boolean nettyEnabled) {
        this.nettyEnabled = nettyEnabled;
    }

    public int getTcpPort() {
        return tcpPort;
    }

    public void setTcpPort(int tcpPort) {
        this.tcpPort = tcpPort;
    }

    public int getBossThreads() {
        return bossThreads;
    }

    public void setBossThreads(int bossThreads) {
        this.bossThreads = bossThreads;
    }

    public int getWorkerThreads() {
        return workerThreads;
    }

    public void setWorkerThreads(int workerThreads) {
        this.workerThreads = workerThreads;
    }

    public int getMaxPacketBytes() {
        return maxPacketBytes;
    }

    public void setMaxPacketBytes(int maxPacketBytes) {
        this.maxPacketBytes = maxPacketBytes;
    }

    public int getSoBacklog() {
        return soBacklog;
    }

    public void setSoBacklog(int soBacklog) {
        this.soBacklog = soBacklog;
    }

    public int getMaxConnections() {
        return maxConnections;
    }

    public void setMaxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
    }

    public String getStorageMode() {
        return storageMode;
    }

    public void setStorageMode(String storageMode) {
        this.storageMode = storageMode;
    }

    public boolean isStorageEnabled() {
        return storageEnabled;
    }

    public void setStorageEnabled(boolean storageEnabled) {
        this.storageEnabled = storageEnabled;
    }

    public String getSessionStoreFilePath() {
        return sessionStoreFilePath;
    }

    public void setSessionStoreFilePath(String sessionStoreFilePath) {
        this.sessionStoreFilePath = sessionStoreFilePath;
    }

    public String getRetainStoreFilePath() {
        return retainStoreFilePath;
    }

    public void setRetainStoreFilePath(String retainStoreFilePath) {
        this.retainStoreFilePath = retainStoreFilePath;
    }

    public boolean isStorageMigrateOnStartup() {
        return storageMigrateOnStartup;
    }

    public void setStorageMigrateOnStartup(boolean storageMigrateOnStartup) {
        this.storageMigrateOnStartup = storageMigrateOnStartup;
    }

    public String getStorageRedisAddress() {
        return storageRedisAddress;
    }

    public void setStorageRedisAddress(String storageRedisAddress) {
        this.storageRedisAddress = storageRedisAddress;
    }

    public String getStorageRedisPassword() {
        return storageRedisPassword;
    }

    public void setStorageRedisPassword(String storageRedisPassword) {
        this.storageRedisPassword = storageRedisPassword;
    }

    public int getStorageRedisDatabase() {
        return storageRedisDatabase;
    }

    public void setStorageRedisDatabase(int storageRedisDatabase) {
        this.storageRedisDatabase = storageRedisDatabase;
    }

    public long getStorageRedisTimeoutMs() {
        return storageRedisTimeoutMs;
    }

    public void setStorageRedisTimeoutMs(long storageRedisTimeoutMs) {
        this.storageRedisTimeoutMs = storageRedisTimeoutMs;
    }

    public String getStorageRedisKeyPrefix() {
        return storageRedisKeyPrefix;
    }

    public void setStorageRedisKeyPrefix(String storageRedisKeyPrefix) {
        this.storageRedisKeyPrefix = storageRedisKeyPrefix;
    }

    public String getStorageDbJdbcUrl() {
        return storageDbJdbcUrl;
    }

    public void setStorageDbJdbcUrl(String storageDbJdbcUrl) {
        this.storageDbJdbcUrl = storageDbJdbcUrl;
    }

    public String getStorageDbUsername() {
        return storageDbUsername;
    }

    public void setStorageDbUsername(String storageDbUsername) {
        this.storageDbUsername = storageDbUsername;
    }

    public String getStorageDbPassword() {
        return storageDbPassword;
    }

    public void setStorageDbPassword(String storageDbPassword) {
        this.storageDbPassword = storageDbPassword;
    }

    public String getStorageDbSchema() {
        return storageDbSchema;
    }

    public void setStorageDbSchema(String storageDbSchema) {
        this.storageDbSchema = storageDbSchema;
    }

    public String getStorageDbTablePrefix() {
        return storageDbTablePrefix;
    }

    public void setStorageDbTablePrefix(String storageDbTablePrefix) {
        this.storageDbTablePrefix = storageDbTablePrefix;
    }

    public int getSessionOfflineMaxMessages() {
        return sessionOfflineMaxMessages;
    }

    public void setSessionOfflineMaxMessages(int sessionOfflineMaxMessages) {
        this.sessionOfflineMaxMessages = sessionOfflineMaxMessages;
    }

    public long getSessionOfflineTtlMs() {
        return sessionOfflineTtlMs;
    }

    public void setSessionOfflineTtlMs(long sessionOfflineTtlMs) {
        this.sessionOfflineTtlMs = sessionOfflineTtlMs;
    }

    public int getRetainMaxMessages() {
        return retainMaxMessages;
    }

    public void setRetainMaxMessages(int retainMaxMessages) {
        this.retainMaxMessages = retainMaxMessages;
    }

    public long getRetainTtlMs() {
        return retainTtlMs;
    }

    public void setRetainTtlMs(long retainTtlMs) {
        this.retainTtlMs = retainTtlMs;
    }

    public boolean isTlsEnabled() {
        return tlsEnabled;
    }

    public void setTlsEnabled(boolean tlsEnabled) {
        this.tlsEnabled = tlsEnabled;
    }

    public int getTlsPort() {
        return tlsPort;
    }

    public void setTlsPort(int tlsPort) {
        this.tlsPort = tlsPort;
    }

    public String getTlsCertChainPath() {
        return tlsCertChainPath;
    }

    public void setTlsCertChainPath(String tlsCertChainPath) {
        this.tlsCertChainPath = tlsCertChainPath;
    }

    public String getTlsPrivateKeyPath() {
        return tlsPrivateKeyPath;
    }

    public void setTlsPrivateKeyPath(String tlsPrivateKeyPath) {
        this.tlsPrivateKeyPath = tlsPrivateKeyPath;
    }

    public String getTlsPrivateKeyPassword() {
        return tlsPrivateKeyPassword;
    }

    public void setTlsPrivateKeyPassword(String tlsPrivateKeyPassword) {
        this.tlsPrivateKeyPassword = tlsPrivateKeyPassword;
    }

    public boolean isAuthEnabled() {
        return authEnabled;
    }

    public void setAuthEnabled(boolean authEnabled) {
        this.authEnabled = authEnabled;
    }

    public String getAuthUsername() {
        return authUsername;
    }

    public void setAuthUsername(String authUsername) {
        this.authUsername = authUsername;
    }

    public String getAuthPassword() {
        return authPassword;
    }

    public void setAuthPassword(String authPassword) {
        this.authPassword = authPassword;
    }

    public String getAuthMode() {
        return authMode;
    }

    public void setAuthMode(String authMode) {
        this.authMode = authMode;
    }

    public String getAuthHttpUrl() {
        return authHttpUrl;
    }

    public void setAuthHttpUrl(String authHttpUrl) {
        this.authHttpUrl = authHttpUrl;
    }

    public long getAuthHttpConnectTimeoutMs() {
        return authHttpConnectTimeoutMs;
    }

    public void setAuthHttpConnectTimeoutMs(long authHttpConnectTimeoutMs) {
        this.authHttpConnectTimeoutMs = authHttpConnectTimeoutMs;
    }

    public long getAuthHttpRequestTimeoutMs() {
        return authHttpRequestTimeoutMs;
    }

    public void setAuthHttpRequestTimeoutMs(long authHttpRequestTimeoutMs) {
        this.authHttpRequestTimeoutMs = authHttpRequestTimeoutMs;
    }

    public String getAuthHttpAuthorizationHeader() {
        return authHttpAuthorizationHeader;
    }

    public void setAuthHttpAuthorizationHeader(String authHttpAuthorizationHeader) {
        this.authHttpAuthorizationHeader = authHttpAuthorizationHeader;
    }

    public boolean isAclEnabled() {
        return aclEnabled;
    }

    public void setAclEnabled(boolean aclEnabled) {
        this.aclEnabled = aclEnabled;
    }

    public String getAclMode() {
        return aclMode;
    }

    public void setAclMode(String aclMode) {
        this.aclMode = aclMode;
    }

    public String getAclHttpUrl() {
        return aclHttpUrl;
    }

    public void setAclHttpUrl(String aclHttpUrl) {
        this.aclHttpUrl = aclHttpUrl;
    }

    public long getAclHttpConnectTimeoutMs() {
        return aclHttpConnectTimeoutMs;
    }

    public void setAclHttpConnectTimeoutMs(long aclHttpConnectTimeoutMs) {
        this.aclHttpConnectTimeoutMs = aclHttpConnectTimeoutMs;
    }

    public long getAclHttpRequestTimeoutMs() {
        return aclHttpRequestTimeoutMs;
    }

    public void setAclHttpRequestTimeoutMs(long aclHttpRequestTimeoutMs) {
        this.aclHttpRequestTimeoutMs = aclHttpRequestTimeoutMs;
    }

    public long getAclHttpRefreshIntervalMs() {
        return aclHttpRefreshIntervalMs;
    }

    public void setAclHttpRefreshIntervalMs(long aclHttpRefreshIntervalMs) {
        this.aclHttpRefreshIntervalMs = aclHttpRefreshIntervalMs;
    }

    public String getAclHttpAuthorizationHeader() {
        return aclHttpAuthorizationHeader;
    }

    public void setAclHttpAuthorizationHeader(String aclHttpAuthorizationHeader) {
        this.aclHttpAuthorizationHeader = aclHttpAuthorizationHeader;
    }

    public boolean isQos1RetransmitEnabled() {
        return qos1RetransmitEnabled;
    }

    public void setQos1RetransmitEnabled(boolean qos1RetransmitEnabled) {
        this.qos1RetransmitEnabled = qos1RetransmitEnabled;
    }

    public long getQos1RetransmitIntervalMs() {
        return qos1RetransmitIntervalMs;
    }

    public void setQos1RetransmitIntervalMs(long qos1RetransmitIntervalMs) {
        this.qos1RetransmitIntervalMs = qos1RetransmitIntervalMs;
    }

    public int getQos1RetransmitMaxAttempts() {
        return qos1RetransmitMaxAttempts;
    }

    public void setQos1RetransmitMaxAttempts(int qos1RetransmitMaxAttempts) {
        this.qos1RetransmitMaxAttempts = qos1RetransmitMaxAttempts;
    }

    public int getInboundQos2PendingMax() {
        return inboundQos2PendingMax;
    }

    public void setInboundQos2PendingMax(int inboundQos2PendingMax) {
        this.inboundQos2PendingMax = inboundQos2PendingMax;
    }

    public List<String> getAclAllowSubscribePrefixes() {
        return aclAllowSubscribePrefixes;
    }

    public void setAclAllowSubscribePrefixes(List<String> aclAllowSubscribePrefixes) {
        this.aclAllowSubscribePrefixes = aclAllowSubscribePrefixes;
    }

    public List<String> getAclAllowPublishPrefixes() {
        return aclAllowPublishPrefixes;
    }

    public void setAclAllowPublishPrefixes(List<String> aclAllowPublishPrefixes) {
        this.aclAllowPublishPrefixes = aclAllowPublishPrefixes;
    }

    public boolean isAclDefaultDeny() {
        return aclDefaultDeny;
    }

    public void setAclDefaultDeny(boolean aclDefaultDeny) {
        this.aclDefaultDeny = aclDefaultDeny;
    }

    public List<String> getAclDenySubscribePrefixes() {
        return aclDenySubscribePrefixes;
    }

    public void setAclDenySubscribePrefixes(List<String> aclDenySubscribePrefixes) {
        this.aclDenySubscribePrefixes = aclDenySubscribePrefixes;
    }

    public List<String> getAclDenyPublishPrefixes() {
        return aclDenyPublishPrefixes;
    }

    public void setAclDenyPublishPrefixes(List<String> aclDenyPublishPrefixes) {
        this.aclDenyPublishPrefixes = aclDenyPublishPrefixes;
    }

    public boolean isEventNotifyEnabled() {
        return eventNotifyEnabled;
    }

    public void setEventNotifyEnabled(boolean eventNotifyEnabled) {
        this.eventNotifyEnabled = eventNotifyEnabled;
    }

    public Map<String, EventForwardConfig> getEventNotifyConfig() {
        return eventNotifyConfig;
    }

    public void setEventNotifyConfig(Map<String, EventForwardConfig> eventNotifyConfig) {
        this.eventNotifyConfig = eventNotifyConfig;
    }

    public List<ForwardRule> getEventNotifyDefaultForwardRules() {
        return eventNotifyDefaultForwardRules;
    }

    public void setEventNotifyDefaultForwardRules(List<ForwardRule> eventNotifyDefaultForwardRules) {
        this.eventNotifyDefaultForwardRules = eventNotifyDefaultForwardRules;
    }

    public boolean isLifecycleNotifyEnabled() {
        return lifecycleNotifyEnabled;
    }

    public void setLifecycleNotifyEnabled(boolean lifecycleNotifyEnabled) {
        this.lifecycleNotifyEnabled = lifecycleNotifyEnabled;
    }

    public String getLifecycleConnectedTopic() {
        return lifecycleConnectedTopic;
    }

    public void setLifecycleConnectedTopic(String lifecycleConnectedTopic) {
        this.lifecycleConnectedTopic = lifecycleConnectedTopic;
    }

    public String getLifecycleOfflineTopic() {
        return lifecycleOfflineTopic;
    }

    public void setLifecycleOfflineTopic(String lifecycleOfflineTopic) {
        this.lifecycleOfflineTopic = lifecycleOfflineTopic;
    }

    public List<ForwardRule> getLifecycleForwardRules() {
        return lifecycleForwardRules;
    }

    public void setLifecycleForwardRules(List<ForwardRule> lifecycleForwardRules) {
        this.lifecycleForwardRules = lifecycleForwardRules;
    }

    public boolean isTraceIdEnabled() { return traceIdEnabled; }
    public void setTraceIdEnabled(boolean traceIdEnabled) { this.traceIdEnabled = traceIdEnabled; }

    public String getTraceIdLogFields() { return traceIdLogFields; }
    public void setTraceIdLogFields(String traceIdLogFields) { this.traceIdLogFields = traceIdLogFields; }

    public int getAuthThreadPoolSize() { return authThreadPoolSize; }
    public void setAuthThreadPoolSize(int authThreadPoolSize) { this.authThreadPoolSize = authThreadPoolSize; }

    public int getAuthThreadPoolQueueCapacity() { return authThreadPoolQueueCapacity; }
    public void setAuthThreadPoolQueueCapacity(int authThreadPoolQueueCapacity) { this.authThreadPoolQueueCapacity = authThreadPoolQueueCapacity; }

    public long getAuthHttpTimeoutMs() { return authHttpTimeoutMs; }
    public void setAuthHttpTimeoutMs(long authHttpTimeoutMs) { this.authHttpTimeoutMs = authHttpTimeoutMs; }

    public double getConnectionWarnThresholdRatio() { return connectionWarnThresholdRatio; }
    public void setConnectionWarnThresholdRatio(double connectionWarnThresholdRatio) { this.connectionWarnThresholdRatio = connectionWarnThresholdRatio; }

    public int getDbPoolMinIdle() { return dbPoolMinIdle; }
    public void setDbPoolMinIdle(int dbPoolMinIdle) { this.dbPoolMinIdle = dbPoolMinIdle; }
    public int getDbPoolMaxSize() { return dbPoolMaxSize; }
    public void setDbPoolMaxSize(int dbPoolMaxSize) { this.dbPoolMaxSize = dbPoolMaxSize; }
    public long getDbPoolConnectionTimeoutMs() { return dbPoolConnectionTimeoutMs; }
    public void setDbPoolConnectionTimeoutMs(long dbPoolConnectionTimeoutMs) { this.dbPoolConnectionTimeoutMs = dbPoolConnectionTimeoutMs; }
    public long getDbPoolIdleTimeoutMs() { return dbPoolIdleTimeoutMs; }
    public void setDbPoolIdleTimeoutMs(long dbPoolIdleTimeoutMs) { this.dbPoolIdleTimeoutMs = dbPoolIdleTimeoutMs; }
    public long getDbPoolMaxLifetimeMs() { return dbPoolMaxLifetimeMs; }
    public void setDbPoolMaxLifetimeMs(long dbPoolMaxLifetimeMs) { this.dbPoolMaxLifetimeMs = dbPoolMaxLifetimeMs; }
    public long getDbPoolLeakDetectionMs() { return dbPoolLeakDetectionMs; }
    public void setDbPoolLeakDetectionMs(long dbPoolLeakDetectionMs) { this.dbPoolLeakDetectionMs = dbPoolLeakDetectionMs; }

    public java.util.Map<String, Integer> getTopicRateLimits() { return topicRateLimits; }
    public void setTopicRateLimits(java.util.Map<String, Integer> topicRateLimits) { this.topicRateLimits = topicRateLimits; }
    public String getTopicRateLimitDefaultStrategy() { return topicRateLimitDefaultStrategy; }
    public void setTopicRateLimitDefaultStrategy(String topicRateLimitDefaultStrategy) { this.topicRateLimitDefaultStrategy = topicRateLimitDefaultStrategy; }
    public java.util.Map<String, String> getTopicRateLimitStrategies() { return topicRateLimitStrategies; }
    public void setTopicRateLimitStrategies(java.util.Map<String, String> topicRateLimitStrategies) { this.topicRateLimitStrategies = topicRateLimitStrategies; }
}
