package com.mars.linker.broker.ui.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * UI 管理模块配置。
 */
@Component
@ConfigurationProperties(prefix = "mars.linker.ui")
public class MarsLinkerUiProperties {

    /**
     * 是否启用 UI 管理模块 API。
     */
    private boolean enabled = true;

    /**
     * 数据采集缓冲区大小。
     */
    private int collectionBufferSize = 1_000;

    /**
     * 监控页默认刷新间隔（毫秒）。
     */
    private long monitorRefreshMs = 5_000L;

    /** 连接指标采集开关。 */
    private boolean connectionEnabled = true;
    /** 消息指标采集开关。 */
    private boolean messageEnabled = true;
    /** 订阅指标采集开关。 */
    private boolean subscriptionEnabled = true;
    /** 系统指标采集开关。 */
    private boolean systemEnabled = true;
    /** 告警评估开关。 */
    private boolean alertEvaluationEnabled = true;

    /** 连接指标采样率（0.0-1.0）。 */
    private double connectionSampleRate = 1.0;
    /** 消息指标采样率（0.0-1.0）。 */
    private double messageSampleRate = 1.0;
    /** 订阅指标采样率（0.0-1.0）。 */
    private double subscriptionSampleRate = 1.0;
    /** 系统指标采样率（0.0-1.0）。 */
    private double systemSampleRate = 1.0;

    /** 聚合窗口（毫秒）。 */
    private long aggregationWindowMs = 1_000L;

    /** 历史数据最大内存（MB）。 */
    private int maxHistoryMemoryMB = 64;
    /** 监控采集线程池大小。 */
    private int monitoringThreadPoolSize = 2;
    /** 推送线程池大小。 */
    private int pushThreadPoolSize = 2;
    /** API 最大查询速率（次/秒）。 */
    private int maxApiQueriesPerSecond = 30;
    /** 最大推送连接数。 */
    private int maxPushConnections = 50;

    /** 持久化模式：memory / file / db。 */
    private String persistMode = "memory";

    /** 是否启用 API 鉴权（默认关闭，需要 JWT token）。 */
    private boolean authEnabled = false;

    /** JWT 签名密钥（HMAC-SHA256，至少 256 位 / 32 字节）。 */
    private String jwtSecretKey = "change-me-to-a-secure-random-secret-key-32bytes!";

    /** Access Token 过期时间（秒），默认 15 分钟。 */
    private int jwtExpireSeconds = 900;

    /** Refresh Token 过期时间（秒）。 */
    private int jwtRefreshExpireSeconds = 86400;

    /** 管理员用户名。 */
    private String adminUsername = "admin";

    /** 管理员密码。 */
    private String adminPassword = "admin";

    /** CORS 允许来源列表，为空时拒绝所有跨域请求。 */
    private List<String> corsAllowedOrigins = new ArrayList<>();

    /** SSE 连接超时时间（分钟），默认 30 分钟。 */
    private int sseTimeoutMinutes = 30;

    /** SSE 心跳保活间隔（秒），默认 900 秒（15 分钟）。 */
    private int sseKeepaliveIntervalSeconds = 900;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getCollectionBufferSize() {
        return collectionBufferSize;
    }

    public void setCollectionBufferSize(int collectionBufferSize) {
        this.collectionBufferSize = collectionBufferSize;
    }

    public long getMonitorRefreshMs() {
        return monitorRefreshMs;
    }

    public void setMonitorRefreshMs(long monitorRefreshMs) {
        this.monitorRefreshMs = monitorRefreshMs;
    }

    public boolean isConnectionEnabled() { return connectionEnabled; }
    public void setConnectionEnabled(boolean connectionEnabled) { this.connectionEnabled = connectionEnabled; }
    public boolean isMessageEnabled() { return messageEnabled; }
    public void setMessageEnabled(boolean messageEnabled) { this.messageEnabled = messageEnabled; }
    public boolean isSubscriptionEnabled() { return subscriptionEnabled; }
    public void setSubscriptionEnabled(boolean subscriptionEnabled) { this.subscriptionEnabled = subscriptionEnabled; }
    public boolean isSystemEnabled() { return systemEnabled; }
    public void setSystemEnabled(boolean systemEnabled) { this.systemEnabled = systemEnabled; }
    public boolean isAlertEvaluationEnabled() { return alertEvaluationEnabled; }
    public void setAlertEvaluationEnabled(boolean alertEvaluationEnabled) { this.alertEvaluationEnabled = alertEvaluationEnabled; }

    public double getConnectionSampleRate() { return connectionSampleRate; }
    public void setConnectionSampleRate(double connectionSampleRate) { this.connectionSampleRate = connectionSampleRate; }
    public double getMessageSampleRate() { return messageSampleRate; }
    public void setMessageSampleRate(double messageSampleRate) { this.messageSampleRate = messageSampleRate; }
    public double getSubscriptionSampleRate() { return subscriptionSampleRate; }
    public void setSubscriptionSampleRate(double subscriptionSampleRate) { this.subscriptionSampleRate = subscriptionSampleRate; }
    public double getSystemSampleRate() { return systemSampleRate; }
    public void setSystemSampleRate(double systemSampleRate) { this.systemSampleRate = systemSampleRate; }

    public long getAggregationWindowMs() { return aggregationWindowMs; }
    public void setAggregationWindowMs(long aggregationWindowMs) { this.aggregationWindowMs = aggregationWindowMs; }

    public int getMaxHistoryMemoryMB() { return maxHistoryMemoryMB; }
    public void setMaxHistoryMemoryMB(int maxHistoryMemoryMB) { this.maxHistoryMemoryMB = maxHistoryMemoryMB; }
    public int getMonitoringThreadPoolSize() { return monitoringThreadPoolSize; }
    public void setMonitoringThreadPoolSize(int monitoringThreadPoolSize) { this.monitoringThreadPoolSize = monitoringThreadPoolSize; }
    public int getPushThreadPoolSize() { return pushThreadPoolSize; }
    public void setPushThreadPoolSize(int pushThreadPoolSize) { this.pushThreadPoolSize = pushThreadPoolSize; }
    public int getMaxApiQueriesPerSecond() { return maxApiQueriesPerSecond; }
    public void setMaxApiQueriesPerSecond(int maxApiQueriesPerSecond) { this.maxApiQueriesPerSecond = maxApiQueriesPerSecond; }
    public int getMaxPushConnections() { return maxPushConnections; }
    public void setMaxPushConnections(int maxPushConnections) { this.maxPushConnections = maxPushConnections; }

    public String getPersistMode() { return persistMode; }
    public void setPersistMode(String persistMode) { this.persistMode = persistMode; }

    public boolean isAuthEnabled() { return authEnabled; }
    public void setAuthEnabled(boolean authEnabled) { this.authEnabled = authEnabled; }

    public String getJwtSecretKey() { return jwtSecretKey; }
    public void setJwtSecretKey(String jwtSecretKey) { this.jwtSecretKey = jwtSecretKey; }
    public int getJwtExpireSeconds() { return jwtExpireSeconds; }
    public void setJwtExpireSeconds(int jwtExpireSeconds) { this.jwtExpireSeconds = jwtExpireSeconds; }
    public int getJwtRefreshExpireSeconds() { return jwtRefreshExpireSeconds; }
    public void setJwtRefreshExpireSeconds(int jwtRefreshExpireSeconds) { this.jwtRefreshExpireSeconds = jwtRefreshExpireSeconds; }
    public String getAdminUsername() { return adminUsername; }
    public void setAdminUsername(String adminUsername) { this.adminUsername = adminUsername; }
    public String getAdminPassword() { return adminPassword; }
    public void setAdminPassword(String adminPassword) { this.adminPassword = adminPassword; }

    public List<String> getCorsAllowedOrigins() { return corsAllowedOrigins; }
    public void setCorsAllowedOrigins(List<String> corsAllowedOrigins) { this.corsAllowedOrigins = corsAllowedOrigins; }

    public int getSseTimeoutMinutes() { return sseTimeoutMinutes; }
    public void setSseTimeoutMinutes(int sseTimeoutMinutes) { this.sseTimeoutMinutes = sseTimeoutMinutes; }

    public int getSseKeepaliveIntervalSeconds() { return sseKeepaliveIntervalSeconds; }
    public void setSseKeepaliveIntervalSeconds(int sseKeepaliveIntervalSeconds) { this.sseKeepaliveIntervalSeconds = sseKeepaliveIntervalSeconds; }
}
