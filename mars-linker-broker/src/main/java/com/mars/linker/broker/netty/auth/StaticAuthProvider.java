package com.mars.linker.broker.netty.auth;

/**
 * 静态账号密码鉴权（兼容现有配置）。
 */
public final class StaticAuthProvider implements AuthProvider {
    private final boolean enabled;
    private final String username;
    private final String password;

    public StaticAuthProvider(boolean enabled, String username, String password) {
        this.enabled = enabled;
        this.username = username;
        this.password = password;
    }

    @Override
    public boolean authenticate(String clientId, String username, String password) {
        if (!enabled) {
            return true;
        }
        if (this.username == null || this.password == null) {
            return false;
        }
        return this.username.equals(username) && this.password.equals(password);
    }
}
