package com.mars.linker.broker.netty;

/**
 * CONNECT 鉴权提供者（可插拔）。
 */
interface AuthProvider {
    boolean authenticate(String clientId, String username, String password);
}
