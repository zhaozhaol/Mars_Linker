package com.mars.linker.broker.netty.auth;

/**
 * CONNECT 鉴权提供者（可插拔）。
 */
public interface AuthProvider extends AutoCloseable {
    public boolean authenticate(String clientId, String username, String password);

    @Override
    default void close() {
        // no-op by default
    }
}
