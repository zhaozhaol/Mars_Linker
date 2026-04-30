package com.mars.linker.broker.ui.monitoring.sampling;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 采样率过滤器：按指标类别控制采集频率。
 * LongAdder 计数器不受影响，仅详细事件采集受采样率控制。
 */
public class SampleRateFilter {

    private final Map<String, AtomicReference<Double>> sampleRates = new ConcurrentHashMap<>();

    public SampleRateFilter() {
        sampleRates.put("connection", new AtomicReference<>(1.0));
        sampleRates.put("message", new AtomicReference<>(1.0));
        sampleRates.put("subscription", new AtomicReference<>(1.0));
        sampleRates.put("system", new AtomicReference<>(1.0));
    }

    public boolean shouldSample(String category) {
        AtomicReference<Double> rate = sampleRates.get(category);
        if (rate == null) return true;
        double r = rate.get();
        if (r >= 1.0) return true;
        if (r <= 0.0) return false;
        return ThreadLocalRandom.current().nextDouble() < r;
    }

    public void updateSampleRate(String category, double rate) {
        sampleRates.computeIfAbsent(category, k -> new AtomicReference<>(1.0)).set(Math.max(0.0, Math.min(1.0, rate)));
    }

    public double getSampleRate(String category) {
        AtomicReference<Double> rate = sampleRates.get(category);
        return rate == null ? 1.0 : rate.get();
    }
}
