package com.mars.linker.broker.netty.trace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * 结构化日志工具，输出固定格式的业务链追踪日志。
 * <p>
 * 职责：在关键业务节点（鉴权、ACL、Session、路由、QoS、持久化）输出包含
 * traceId、clientId、operation、durationMs、result 五个字段的结构化日志。
 * </p>
 * <p>
 * 日志级别规范：
 * - INFO：关键节点（连接建立/断开、订阅/取消订阅、消息路由完成、持久化完成）
 * - WARN：异常重试（鉴权超时重试、存储写入重试、QoS 重传）
 * - ERROR：操作失败（鉴权失败、ACL 拒绝、存储异常、连接异常关闭）
 * - DEBUG：详细链路（帧解码细节、消息匹配过程、线程调度）
 * </p>
 * <p>
 * 线程安全策略：所有方法为静态方法，MDC 为线程本地存储，无共享可变状态。
 * </p>
 */
public final class StructuredLogger {

    private static final String CATEGORY = "broker";

    private StructuredLogger() {
    }

    /**
     * 输出 INFO 级别结构化日志。
     *
     * @param module     模块标识（如 AUTH、ACL、SESSION、ROUTER、QOS、STORE）
     * @param clientId   客户端标识
     * @param operation  操作类型（如 connect、subscribe、publish、persist）
     * @param durationMs 操作耗时（毫秒）
     * @param result     操作结果（如 SUCCESS、FAILURE、TIMEOUT）
     * @param message    附加消息（可为 null）
     */
    public static void info(String module, String clientId, String operation,
                            long durationMs, String result, String message) {
        Logger logger = LoggerFactory.getLogger(CATEGORY + "." + module);
        if (logger.isInfoEnabled()) {
            logger.info(formatLog(clientId, operation, durationMs, result, message));
        }
    }

    public static void info(String module, String clientId, String operation,
                            long durationMs, String result) {
        info(module, clientId, operation, durationMs, result, null);
    }

    /**
     * 输出 WARN 级别结构化日志。
     */
    public static void warn(String module, String clientId, String operation,
                            long durationMs, String result, String message) {
        Logger logger = LoggerFactory.getLogger(CATEGORY + "." + module);
        if (logger.isWarnEnabled()) {
            logger.warn(formatLog(clientId, operation, durationMs, result, message));
        }
    }

    public static void warn(String module, String clientId, String operation,
                            long durationMs, String result) {
        warn(module, clientId, operation, durationMs, result, null);
    }

    /**
     * 输出 ERROR 级别结构化日志。
     */
    public static void error(String module, String clientId, String operation,
                             long durationMs, String result, String message) {
        Logger logger = LoggerFactory.getLogger(CATEGORY + "." + module);
        if (logger.isErrorEnabled()) {
            logger.error(formatLog(clientId, operation, durationMs, result, message));
        }
    }

    public static void error(String module, String clientId, String operation,
                             long durationMs, String result) {
        error(module, clientId, operation, durationMs, result, null);
    }

    /**
     * 输出 DEBUG 级别结构化日志。
     */
    public static void debug(String module, String clientId, String operation,
                             long durationMs, String result, String message) {
        Logger logger = LoggerFactory.getLogger(CATEGORY + "." + module);
        if (logger.isDebugEnabled()) {
            logger.debug(formatLog(clientId, operation, durationMs, result, message));
        }
    }

    public static void debug(String module, String clientId, String operation,
                             long durationMs, String result) {
        debug(module, clientId, operation, durationMs, result, null);
    }

    /**
     * 格式化结构化日志：{traceId=xxx, clientId=xxx, operation=xxx, durationMs=xxx, result=xxx[, message=xxx]}。
     */
    private static String formatLog(String clientId, String operation,
                                    long durationMs, String result, String message) {
        String traceId = MDC.get(TraceContext.MDC_KEY);
        if (traceId == null) {
            traceId = "N/A";
        }
        StringBuilder sb = new StringBuilder(128);
        sb.append("{traceId=").append(traceId);
        sb.append(", clientId=").append(clientId != null ? clientId : "N/A");
        sb.append(", operation=").append(operation != null ? operation : "N/A");
        sb.append(", durationMs=").append(durationMs);
        sb.append(", result=").append(result != null ? result : "N/A");
        if (message != null && !message.isEmpty()) {
            sb.append(", message=").append(message);
        }
        sb.append("}");
        return sb.toString();
    }
}
