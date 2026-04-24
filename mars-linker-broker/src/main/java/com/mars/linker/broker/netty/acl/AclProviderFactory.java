package com.mars.linker.broker.netty.acl;

import com.mars.linker.broker.config.MarsLinkerMqttBrokerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;

/**
 * 根据 {@link MarsLinkerMqttBrokerProperties} 构建 {@link AclProvider} 实例（静态或 HTTP 动态）。
 */
public final class AclProviderFactory {
    private static final Logger log = LoggerFactory.getLogger(AclProviderFactory.class);

    private AclProviderFactory() {
    }

    public static AclProvider create(MarsLinkerMqttBrokerProperties p) {
        if (p == null || !p.isAclEnabled()) {
            return new PrefixAclProvider(false,
                    Collections.emptyList(),
                    Collections.emptyList(),
                    false,
                    Collections.emptyList(),
                    Collections.emptyList());
        }
        String mode = p.getAclMode() == null ? "static" : p.getAclMode().trim();
        if ("http".equalsIgnoreCase(mode)) {
            String url = p.getAclHttpUrl();
            if (url == null || url.isEmpty()) {
                // 仅在配置了 URL 时才启用 HTTP 动态 ACL；否则回退到默认静态 ACL 语义。
                log.warn("aclMode=http 但未配置 aclHttpUrl，将回退到 static ACL");
                return new PrefixAclProvider(true,
                        p.getAclAllowSubscribePrefixes(),
                        p.getAclAllowPublishPrefixes(),
                        p.isAclDefaultDeny(),
                        p.getAclDenySubscribePrefixes(),
                        p.getAclDenyPublishPrefixes());
            }
            return new HttpAclProvider(
                    url,
                    p.getAclHttpConnectTimeoutMs(),
                    p.getAclHttpRequestTimeoutMs(),
                    p.getAclHttpRefreshIntervalMs(),
                    p.getAclHttpAuthorizationHeader()
            );
        }
        if (!"static".equalsIgnoreCase(mode) && !mode.isEmpty()) {
            log.warn("未知 aclMode={}，将按 static 处理", mode);
        }
        return new PrefixAclProvider(true,
                p.getAclAllowSubscribePrefixes(),
                p.getAclAllowPublishPrefixes(),
                p.isAclDefaultDeny(),
                p.getAclDenySubscribePrefixes(),
                p.getAclDenyPublishPrefixes());
    }
}

