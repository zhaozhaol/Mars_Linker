package com.mars.linker.broker.netty.protocol;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 精确主题消息速率限流管理器。
 * <p>
 * 职责：
 * - 管理每主题独立令牌桶（ConcurrentHashMap）
 * - 限流检查在 ACL 之后、消息路由之前
 * - 支持运行时动态增删改限流配置
 * - 超限策略：drop=丢弃超限消息, disconnect=断开发布者连接
 * </p>
 * <p>
 * 线程安全策略：ConcurrentHashMap 无锁读 + TokenBucket CAS 扣减，不阻塞 Netty Worker 线程。
 * </p>
 */
public class TopicRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(TopicRateLimiter.class);

    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();
    private volatile String defaultStrategy = "drop";
    private final ConcurrentHashMap<String, String> strategies = new ConcurrentHashMap<>();

    /**
     * 从配置初始化限流规则。
     *
     * @param rateLimits      精确主题→每秒最大消息数
     * @param defaultStrategy 默认超限策略（drop/disconnect）
     * @param perTopicStrategies 每主题策略覆盖
     */
    public void configure(Map<String, Integer> rateLimits, String defaultStrategy,
                          Map<String, String> perTopicStrategies) {
        buckets.clear();
        strategies.clear();

        if (rateLimits != null) {
            for (Map.Entry<String, Integer> entry : rateLimits.entrySet()) {
                String topic = entry.getKey();
                int rate = entry.getValue();
                if (topic != null && !topic.isEmpty() && !topic.contains("#") && !topic.contains("+")
                        && rate > 0) {
                    buckets.put(topic, new TokenBucket(rate));
                    log.info("主题限流规则: topic={}, rate={}/s", topic, rate);
                } else {
                    log.warn("跳过无效限流配置: topic={}, rate={}", topic, rate);
                }
            }
        }

        if (defaultStrategy != null) {
            this.defaultStrategy = defaultStrategy;
        }

        if (perTopicStrategies != null) {
            for (Map.Entry<String, String> entry : perTopicStrategies.entrySet()) {
                String strategy = entry.getValue();
                if ("drop".equals(strategy) || "disconnect".equals(strategy)) {
                    strategies.put(entry.getKey(), strategy);
                } else {
                    log.warn("跳过无效限流策略: topic={}, strategy={}", entry.getKey(), strategy);
                }
            }
        }

        log.info("主题限流初始化完成: {} 条规则, 默认策略={}", buckets.size(), this.defaultStrategy);
    }

    /**
     * 检查主题是否允许通过。
     *
     * @param topic 精确主题
     * @return true 表示允许，false 表示超限
     */
    public boolean tryAcquire(String topic) {
        TokenBucket bucket = buckets.get(topic);
        if (bucket == null) {
            return true;
        }
        return bucket.tryAcquire();
    }

    /**
     * 判断是否有限流规则配置。
     */
    public boolean hasLimit(String topic) {
        return buckets.containsKey(topic);
    }

    /**
     * 获取主题的超限策略。
     *
     * @param topic 精确主题
     * @return 策略（drop/disconnect）
     */
    public String getStrategy(String topic) {
        String strategy = strategies.get(topic);
        return strategy != null ? strategy : defaultStrategy;
    }

    /**
     * 运行时动态添加/更新限流规则。
     */
    public void addOrUpdateLimit(String topic, int maxPerSecond, String strategy) {
        TokenBucket existing = buckets.get(topic);
        if (existing != null) {
            existing.updateRate(maxPerSecond);
        } else {
            buckets.put(topic, new TokenBucket(maxPerSecond));
        }
        if (strategy != null && ("drop".equals(strategy) || "disconnect".equals(strategy))) {
            strategies.put(topic, strategy);
        }
        log.info("动态更新限流规则: topic={}, rate={}/s, strategy={}", topic, maxPerSecond, strategy);
    }

    /**
     * 运行时动态移除限流规则。
     */
    public void removeLimit(String topic) {
        buckets.remove(topic);
        strategies.remove(topic);
        log.info("动态移除限流规则: topic={}", topic);
    }

    /**
     * 获取所有限流主题的统计快照。
     */
    public Map<String, TopicRateLimitStats> getStats() {
        Map<String, TopicRateLimitStats> result = new LinkedHashMap<>();
        for (Map.Entry<String, TokenBucket> entry : buckets.entrySet()) {
            String topic = entry.getKey();
            TokenBucket bucket = entry.getValue();
            result.put(topic, new TopicRateLimitStats(
                    topic,
                    bucket.getMaxPerSecond(),
                    bucket.getAvailableTokens(),
                    bucket.getAllowedCount(),
                    bucket.getRejectedCount(),
                    getStrategy(topic)
            ));
        }
        return result;
    }

    /**
     * 获取单个主题的统计快照。
     */
    public TopicRateLimitStats getStats(String topic) {
        TokenBucket bucket = buckets.get(topic);
        if (bucket == null) {
            return null;
        }
        return new TopicRateLimitStats(
                topic,
                bucket.getMaxPerSecond(),
                bucket.getAvailableTokens(),
                bucket.getAllowedCount(),
                bucket.getRejectedCount(),
                getStrategy(topic)
        );
    }

    public int getRuleCount() {
        return buckets.size();
    }

    /**
     * 限流统计快照。
     */
    public static class TopicRateLimitStats {
        public final String topic;
        public final int maxPerSecond;
        public final int availableTokens;
        public final long allowedCount;
        public final long rejectedCount;
        public final String strategy;

        public TopicRateLimitStats(String topic, int maxPerSecond, int availableTokens,
                                  long allowedCount, long rejectedCount, String strategy) {
            this.topic = topic;
            this.maxPerSecond = maxPerSecond;
            this.availableTokens = availableTokens;
            this.allowedCount = allowedCount;
            this.rejectedCount = rejectedCount;
            this.strategy = strategy;
        }
    }
}
