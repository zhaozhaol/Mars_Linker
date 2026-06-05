package com.mars.linker.broker.netty.trace;

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.AttributeKey;
import org.slf4j.MDC;

import java.util.concurrent.Callable;
import java.util.concurrent.ThreadLocalRandom;
import java.util.Map;

/**
 * 业务链追踪上下文管理器。
 * <p>
 * 职责：为每个 MQTT 连接/消息处理链路生成唯一 traceId，并通过 Channel 属性
 * 和 SLF4j MDC 双重存储，实现跨线程传播和日志自动输出。
 * </p>
 * <p>
 * 设计决策：
 * - traceId 格式为 {8位短连接ID}-{6位时间戳后6位}-{4位随机数}，兼顾可读性和唯一性
 * - Channel 属性存储保证同一连接的 traceId 稳定，MDC 桥接实现日志框架自动输出
 * - wrapRunnable/wrapCallable 实现跨线程 traceId 传播（异步鉴权线程池、QoS 重传定时任务等）
 * </p>
 * <p>
 * 线程安全策略：
 * - Channel 属性由 Netty 保证线程安全
 * - MDC 使用 InheritableThreadLocal，但跨线程池需要显式传播
 * - 所有方法均为静态方法，无共享可变状态
 * </p>
 */
public final class TraceContext {

    static final AttributeKey<String> TRACE_ID = AttributeKey.valueOf("mqtt_trace_id");
    static final String MDC_KEY = "traceId";

    private TraceContext() {
    }

    /**
     * 初始化 traceId：若 Channel 属性中已有则复用，否则生成新的 traceId 并写入 Channel 属性和 MDC。
     *
     * @param ctx Netty Channel 上下文
     * @return 生成的或已存在的 traceId
     */
    public static String initTraceId(ChannelHandlerContext ctx) {
        String existing = ctx.channel().attr(TRACE_ID).get();
        if (existing != null) {
            MDC.put(MDC_KEY, existing);
            return existing;
        }
        String traceId = generateTraceId(ctx);
        ctx.channel().attr(TRACE_ID).set(traceId);
        MDC.put(MDC_KEY, traceId);
        return traceId;
    }

    /**
     * 获取当前 Channel 的 traceId，优先从 MDC 读取，其次从 Channel 属性读取。
     *
     * @param ctx Netty Channel 上下文
     * @return traceId，不存在时返回 "N/A"
     */
    public static String getTraceId(ChannelHandlerContext ctx) {
        String fromMdc = MDC.get(MDC_KEY);
        if (fromMdc != null) {
            return fromMdc;
        }
        if (ctx != null) {
            String fromChannel = ctx.channel().attr(TRACE_ID).get();
            if (fromChannel != null) {
                MDC.put(MDC_KEY, fromChannel);
                return fromChannel;
            }
        }
        return "N/A";
    }

    /**
     * 获取当前 MDC 中的 traceId（不依赖 Channel，用于非 Netty 线程）。
     *
     * @return traceId，不存在时返回 "N/A"
     */
    public static String getTraceId() {
        String fromMdc = MDC.get(MDC_KEY);
        return fromMdc != null ? fromMdc : "N/A";
    }

    /**
     * 将 traceId 设置到 MDC（用于跨线程传播后恢复上下文）。
     *
     * @param traceId 要设置的 traceId
     */
    public static void setTraceId(String traceId) {
        if (traceId != null) {
            MDC.put(MDC_KEY, traceId);
        }
    }

    /**
     * 清理 MDC 中的 traceId（在 ChannelInactive 或请求处理完成后调用，防止线程复用时的上下文泄漏）。
     */
    public static void clearTraceId() {
        MDC.remove(MDC_KEY);
    }

    /**
     * 包装 Runnable，在目标线程中恢复 traceId 到 MDC，执行完毕后清理 MDC。
     * <p>
     * 用于异步鉴权线程池、QoS 重传定时任务等跨线程场景的 traceId 传播。
     * </p>
     *
     * @param traceId 当前链路的 traceId
     * @param task    要执行的任务
     * @return 包装后的 Runnable
     */
    public static Runnable wrapRunnable(String traceId, Runnable task) {
        if (traceId == null || "N/A".equals(traceId)) {
            return task;
        }
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> oldContext = MDC.getCopyOfContextMap();
            try {
                if (contextMap != null) {
                    MDC.setContextMap(contextMap);
                }
                MDC.put(MDC_KEY, traceId);
                task.run();
            } finally {
                if (oldContext != null) {
                    MDC.setContextMap(oldContext);
                } else {
                    MDC.clear();
                }
            }
        };
    }

    /**
     * 包装 Callable，在目标线程中恢复 traceId 到 MDC，执行完毕后清理 MDC，异常正常传播不吞没。
     * <p>
     * 用于带返回值的异步任务（如 Future 提交）的 traceId 传播。
     * </p>
     *
     * @param traceId 当前链路的 traceId
     * @param task    要执行的带返回值任务
     * @param <V>     返回值类型
     * @return 包装后的 Callable
     */
    public static <V> Callable<V> wrapCallable(String traceId, Callable<V> task) {
        if (traceId == null || "N/A".equals(traceId)) {
            return task;
        }
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> oldContext = MDC.getCopyOfContextMap();
            try {
                if (contextMap != null) {
                    MDC.setContextMap(contextMap);
                }
                MDC.put(MDC_KEY, traceId);
                return task.call();
            } finally {
                if (oldContext != null) {
                    MDC.setContextMap(oldContext);
                } else {
                    MDC.clear();
                }
            }
        };
    }

    /**
     * 生成 traceId，格式为 {8位短连接ID}-{6位时间戳后6位}-{4位随机数}。
     */
    private static String generateTraceId(ChannelHandlerContext ctx) {
        String channelId = ctx.channel().id().asShortText();
        long timestamp = System.currentTimeMillis() % 1_000_000;
        int random = ThreadLocalRandom.current().nextInt(10_000);
        return String.format("%s-%06d-%04d", channelId, timestamp, random);
    }
}
