package com.mars.linker.broker.netty.trace;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

/**
 * Spring TaskDecorator 实现：在提交线程中捕获当前 MDC traceId，
 * 委托给 {@link TraceContext#wrapRunnable} 统一包装，实现跨线程池 traceId 自动传播。
 * <p>
 * 适用范围：Spring {@code ThreadPoolTaskExecutor}（通过 setTaskDecorator 配置）。
 * 不适用：JDK 原生 {@code ThreadPoolExecutor}（需手动使用 {@link TraceContext#wrapRunnable}）。
 * </p>
 * <p>
 * 线程安全策略：MDC 读取在提交线程上执行，写入在目标线程上执行，无并发问题。
 * </p>
 */
public class TraceContextTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        String traceId = MDC.get(TraceContext.MDC_KEY);
        return TraceContext.wrapRunnable(traceId, runnable);
    }
}