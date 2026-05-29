package com.mars.linker.broker.netty.auth;

/**
 * 鉴权完成回调函数式接口，在 EventLoop 线程上执行。
 * <p>
 * 使用方式：异步鉴权完成后调用 {@code callback.onResult(authResult)}，
 * 回调逻辑将在对应 Channel 的 EventLoop 线程上执行，保证线程安全。
 * </p>
 */
@FunctionalInterface
public interface AuthCallback {

    /**
     * 鉴权完成回调。
     *
     * @param result 鉴权结果
     */
    void onResult(AuthResult result);
}
