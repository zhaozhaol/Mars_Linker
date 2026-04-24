package com.mars.linker.broker.netty.acl;

import java.util.Collections;
import java.util.List;

/**
 * 前缀 ACL（兼容现有配置语义）。
 */
public final class PrefixAclProvider implements AclProvider {
    private final boolean enabled;
    private final List<String> allowSubscribePrefixes;
    private final List<String> allowPublishPrefixes;
    private final boolean defaultDeny;
    private final List<String> denySubscribePrefixes;
    private final List<String> denyPublishPrefixes;

    public PrefixAclProvider(boolean enabled,
                      List<String> allowSubscribePrefixes,
                      List<String> allowPublishPrefixes,
                      boolean defaultDeny,
                      List<String> denySubscribePrefixes,
                      List<String> denyPublishPrefixes) {
        this.enabled = enabled;
        this.allowSubscribePrefixes = allowSubscribePrefixes == null ? Collections.emptyList() : allowSubscribePrefixes;
        this.allowPublishPrefixes = allowPublishPrefixes == null ? Collections.emptyList() : allowPublishPrefixes;
        this.defaultDeny = defaultDeny;
        this.denySubscribePrefixes = denySubscribePrefixes == null ? Collections.emptyList() : denySubscribePrefixes;
        this.denyPublishPrefixes = denyPublishPrefixes == null ? Collections.emptyList() : denyPublishPrefixes;
    }

    @Override
    public boolean allowsSubscribe(String topicFilter) {
        if (!enabled) {
            return true;
        }
        if (matchesAnyPrefix(topicFilter, denySubscribePrefixes)) {
            return false;
        }
        if (allowSubscribePrefixes.isEmpty()) {
            return !defaultDeny;
        }
        return matchesAnyPrefix(topicFilter, allowSubscribePrefixes);
    }

    @Override
    public boolean allowsPublish(String topic) {
        if (!enabled) {
            return true;
        }
        if (matchesAnyPrefix(topic, denyPublishPrefixes)) {
            return false;
        }
        if (allowPublishPrefixes.isEmpty()) {
            return !defaultDeny;
        }
        return matchesAnyPrefix(topic, allowPublishPrefixes);
    }

    private static boolean matchesAnyPrefix(String value, List<String> prefixes) {
        if (value == null || prefixes == null || prefixes.isEmpty()) {
            return false;
        }
        for (String p : prefixes) {
            if (p != null && !p.isEmpty() && value.startsWith(p)) {
                return true;
            }
        }
        return false;
    }
}
