package com.mars.linker.broker.netty.trace;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 语义化线程命名工厂，生成格式为 {prefix}-{递增序号} 的线程名。
 * <p>
 * 替代使用时间戳的线程命名方式，便于线程 dump 分析和日志排查。
 * </p>
 *
 * <p>使用示例：
 * <pre>
 *   ThreadFactory factory = new NamedThreadFactory("broker-auth-worker", true);
 *   // 生成线程名：broker-auth-worker-1, broker-auth-worker-2, ...
 * </pre>
 * </p>
 */
public class NamedThreadFactory implements ThreadFactory {

    private final String prefix;
    private final boolean daemon;
    private final AtomicInteger counter = new AtomicInteger(1);

    /**
     * @param prefix  线程名前缀（如 "broker-auth-worker"）
     * @param daemon  是否为守护线程
     */
    public NamedThreadFactory(String prefix, boolean daemon) {
        this.prefix = prefix;
        this.daemon = daemon;
    }

    /**
     * @param prefix 线程名前缀，默认非守护线程
     */
    public NamedThreadFactory(String prefix) {
        this(prefix, false);
    }

    @Override
    public Thread newThread(Runnable r) {
        Thread t = new Thread(r, prefix + "-" + counter.getAndIncrement());
        t.setDaemon(daemon);
        return t;
    }
}
