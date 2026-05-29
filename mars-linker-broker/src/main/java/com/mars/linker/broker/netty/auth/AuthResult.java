package com.mars.linker.broker.netty.auth;

/**
 * 鉴权结果封装，供异步鉴权回调使用。
 *
 * @param success    鉴权是否通过
 * @param durationMs 鉴权耗时（毫秒）
 * @param error      鉴权异常（无异常时为 null）
 */
public class AuthResult {

    private final boolean success;
    private final long durationMs;
    private final Throwable error;

    public AuthResult(boolean success, long durationMs, Throwable error) {
        this.success = success;
        this.durationMs = durationMs;
        this.error = error;
    }

    public boolean isSuccess() { return success; }
    public long getDurationMs() { return durationMs; }
    public Throwable getError() { return error; }
}
