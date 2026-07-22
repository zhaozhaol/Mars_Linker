package com.mars.linker.broker.netty.protocol;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 缓存 topic 字符串的 UTF-8 编码字节，避免热路径上重复编码。
 * <p>
 * MQTT 场景中同一 topic 通常被反复发布（如传感器周期上报），
 * 缓存命中率极高。设有软上限避免无界增长。
 */
public final class TopicEncodingCache {
    private static final int MAX_SIZE = 8192;
    private static final ConcurrentHashMap<String, byte[]> CACHE = new ConcurrentHashMap<>();

    private TopicEncodingCache() {
    }

    /**
     * 返回 topic 的 UTF-8 编码字节。优先从缓存获取，缓存未命中时编码并缓存。
     * 当缓存条目超过 {@link #MAX_SIZE} 时，新 topic 不缓存但仍正确返回编码结果。
     */
    public static byte[] get(String topic) {
        byte[] bytes = CACHE.get(topic);
        if (bytes != null) {
            return bytes;
        }
        bytes = topic.getBytes(StandardCharsets.UTF_8);
        if (CACHE.size() < MAX_SIZE) {
            byte[] existing = CACHE.putIfAbsent(topic, bytes);
            if (existing != null) {
                bytes = existing;
            }
        }
        return bytes;
    }

    /** 仅供测试清空缓存。 */
    static void clearForTests() {
        CACHE.clear();
    }
}
