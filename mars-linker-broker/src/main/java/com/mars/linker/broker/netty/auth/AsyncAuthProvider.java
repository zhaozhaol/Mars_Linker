package com.mars.linker.broker.netty.auth;

import com.mars.linker.broker.netty.trace.NamedThreadFactory;
import com.mars.linker.broker.netty.trace.StructuredLogger;
import com.mars.linker.broker.netty.trace.TraceContext;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 异步鉴权提供者：将 HTTP 鉴权调用从 Netty Worker 线程卸载到独立有界线程池。
 * <p>
 * 核心设计决策：
 * - 使用独立 ThreadPoolExecutor，与 Netty Worker 线程完全隔离
 * - 线程池满时使用 AbortPolicy + 快速失败（返回 CONNACK 0x03）
 * - 鉴权完成后通过 Channel.eventLoop().execute() 回调写入 CONNACK，保证线程安全
 * - 超时保护通过 EventLoop schedule 实现，与鉴权执行互斥（AtomicBoolean CAS）
 * </p>
 * <p>
 * 线程安全策略：
 * - Channel 属性读写均在 EventLoop 线程上执行，无并发问题
 * - traceId 通过 Runnable 包装传播，不依赖 ThreadLocal
 * - 鉴权成功/超时/异常三者通过 AtomicBoolean CAS 互斥，保证不重复回调
 * </p>
 */
public class AsyncAuthProvider implements AuthProvider {

    private static final Logger log = LoggerFactory.getLogger(AsyncAuthProvider.class);

    private final HttpAuthProvider delegate;
    private final ThreadPoolExecutor authExecutor;
    private final long authTimeoutMs;

    /**
     * @param delegate      同步 HTTP 鉴权提供者
     * @param poolSize      线程池核心线程数
     * @param queueCapacity 线程池队列容量
     * @param timeoutMs     鉴权超时（毫秒）
     */
    public AsyncAuthProvider(HttpAuthProvider delegate, int poolSize,
                             int queueCapacity, long timeoutMs) {
        this.delegate = delegate;
        this.authTimeoutMs = Math.max(1L, timeoutMs);
        this.authExecutor = new ThreadPoolExecutor(
                poolSize, poolSize,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(queueCapacity),
                new NamedThreadFactory("broker-auth-worker", false),
                new ThreadPoolExecutor.AbortPolicy()
        );
        log.info("异步鉴权线程池初始化: poolSize={}, queueCapacity={}, timeoutMs={}",
                poolSize, queueCapacity, timeoutMs);
    }

    /**
     * 同步鉴权（不推荐在 Netty Worker 线程上调用，仅作兼容接口实现）。
     */
    @Override
    public boolean authenticate(String clientId, String username, String password) {
        return delegate.authenticate(clientId, username, password);
    }

    /**
     * 异步鉴权：提交到鉴权线程池执行，结果通过 EventLoop 回调。
     * <p>
     * 执行流程：
     * 1. 在 EventLoop 上调度超时任务
     * 2. 将鉴权提交到线程池执行
     * 3. 鉴权完成后通过 EventLoop 回调通知结果
     * 4. 超时或异常时同样通过 EventLoop 回调通知
     * </p>
     *
     * @param ctx       Netty Channel 上下文
     * @param clientId  客户端标识
     * @param username  用户名
     * @param password  密码
     * @param callback  鉴权结果回调（在 EventLoop 线程上执行）
     */
    public void authenticateAsync(ChannelHandlerContext ctx, String clientId,
                                  String username, String password,
                                  AuthCallback callback) {
        String traceId = TraceContext.getTraceId(ctx);
        long startTimeMs = System.currentTimeMillis();
        AtomicBoolean completed = new AtomicBoolean(false);

        ScheduledFuture<?> timeoutFuture = ctx.channel().eventLoop().schedule(() -> {
            if (completed.compareAndSet(false, true)) {
                long durationMs = System.currentTimeMillis() - startTimeMs;
                StructuredLogger.warn("AUTH", clientId, "auth_timeout",
                        durationMs, "TIMEOUT");
                callback.onResult(new AuthResult(false, durationMs,
                        new TimeoutException("Auth timeout after " + authTimeoutMs + "ms")));
            }
        }, authTimeoutMs, TimeUnit.MILLISECONDS);

        try {
            authExecutor.execute(TraceContext.wrapRunnable(traceId, () -> {
                try {
                    boolean success = delegate.authenticate(clientId, username, password);
                    long durationMs = System.currentTimeMillis() - startTimeMs;
                    timeoutFuture.cancel(false);
                    if (completed.compareAndSet(false, true)) {
                        if (ctx.channel().isActive()) {
                            ctx.channel().eventLoop().execute(() -> {
                                if (success) {
                                    StructuredLogger.info("AUTH", clientId, "auth_success",
                                            durationMs, "SUCCESS");
                                } else {
                                    StructuredLogger.warn("AUTH", clientId, "auth_rejected",
                                            durationMs, "REJECTED");
                                }
                                callback.onResult(new AuthResult(success, durationMs, null));
                            });
                        } else {
                            log.debug("鉴权完成但 Channel 已关闭，丢弃结果: clientId={}", clientId);
                        }
                    }
                } catch (Exception e) {
                    long durationMs = System.currentTimeMillis() - startTimeMs;
                    timeoutFuture.cancel(false);
                    if (completed.compareAndSet(false, true)) {
                        StructuredLogger.error("AUTH", clientId, "auth_error",
                                durationMs, "ERROR", e.getMessage());
                        if (ctx.channel().isActive()) {
                            ctx.channel().eventLoop().execute(() ->
                                    callback.onResult(new AuthResult(false, durationMs, e)));
                        }
                    }
                }
            }));
        } catch (RejectedExecutionException e) {
            timeoutFuture.cancel(false);
            if (completed.compareAndSet(false, true)) {
                long durationMs = System.currentTimeMillis() - startTimeMs;
                StructuredLogger.warn("AUTH", clientId, "auth_pool_full",
                        durationMs, "POOL_FULL");
                callback.onResult(new AuthResult(false, durationMs,
                        new RejectedExecutionException("Auth thread pool full")));
            }
        }
    }

    /**
     * 获取鉴权线程池当前活跃线程数（供监控使用）。
     */
    public int getActiveCount() {
        return authExecutor.getActiveCount();
    }

    /**
     * 获取鉴权线程池队列当前等待任务数（供监控使用）。
     */
    public int getQueueSize() {
        return authExecutor.getQueue().size();
    }

    @Override
    public void close() {
        authExecutor.shutdownNow();
        try {
            if (!authExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("鉴权线程池未在 5 秒内优雅关闭");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        delegate.close();
    }
}
