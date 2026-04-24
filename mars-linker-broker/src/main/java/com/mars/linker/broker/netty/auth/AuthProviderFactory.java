package com.mars.linker.broker.netty.auth;

import com.mars.linker.broker.config.MarsLinkerMqttBrokerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 根据 {@link MarsLinkerMqttBrokerProperties} 构建 {@link AuthProvider} 实例（静态或 HTTP 回调）。
 */
public final class AuthProviderFactory {
    private static final Logger log = LoggerFactory.getLogger(AuthProviderFactory.class);

    private AuthProviderFactory() {
    }

    public static AuthProvider create(MarsLinkerMqttBrokerProperties p) {
        if (p == null || !p.isAuthEnabled()) {
            return new StaticAuthProvider(false, null, null);
        }
        String mode = p.getAuthMode() == null ? "static" : p.getAuthMode().trim();
        if ("http".equalsIgnoreCase(mode)) {
            String url = p.getAuthHttpUrl();
            if (url == null || url.isEmpty()) {
                // 仅在配置了 URL 时才启用 HTTP 动态鉴权；否则回退到默认静态鉴权语义。
                log.warn("authMode=http 但未配置 authHttpUrl，将回退到 static 鉴权");
                return new StaticAuthProvider(true, p.getAuthUsername(), p.getAuthPassword());
            }
            return new HttpAuthProvider(
                    url,
                    p.getAuthHttpConnectTimeoutMs(),
                    p.getAuthHttpRequestTimeoutMs(),
                    p.getAuthHttpAuthorizationHeader()
            );
        }
        if (!"static".equalsIgnoreCase(mode) && !mode.isEmpty()) {
            log.warn("未知 authMode={}，将按 static 处理", mode);
        }
        return new StaticAuthProvider(true, p.getAuthUsername(), p.getAuthPassword());
    }
}
