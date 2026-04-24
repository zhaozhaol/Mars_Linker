package com.mars.linker.broker.netty.auth;

/**
 * CONNECT 鉴权提供者（可插拔）。
 */
public interface AuthProvider {
    public boolean authenticate(String clientId, String username, String password);
}
