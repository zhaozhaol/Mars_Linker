package com.mars.linker.broker.netty;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * HTTP 动态 ACL（可热更新）。
 * <p>
 * 从配置 URL 拉取 ACL 规则 JSON，并按 refreshInterval 周期性刷新；刷新成功后原子替换内存规则，无需重启。
 * </p>
 * <p>
 * 失败策略：拉取失败/解析失败时保留上一版规则；启动阶段若尚未拉取成功，使用“默认拒绝（deny all）”。
 * </p>
 */
final class HttpAclProvider implements AclProvider {
    private static final Logger log = LoggerFactory.getLogger(HttpAclProvider.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final URI endpoint;
    private final Duration requestTimeout;
    private final String authorizationHeader;
    private final HttpClient httpClient;
    private final ScheduledExecutorService scheduler;
    private final long refreshIntervalMs;

    private final AtomicReference<PrefixAclProvider> delegate = new AtomicReference<>(
            new PrefixAclProvider(true,
                    Collections.emptyList(),
                    Collections.emptyList(),
                    true,
                    Collections.emptyList(),
                    Collections.emptyList())
    );

    HttpAclProvider(String url,
                    long connectTimeoutMs,
                    long requestTimeoutMs,
                    long refreshIntervalMs,
                    String authorizationHeader) {
        this.endpoint = URI.create(url);
        this.requestTimeout = Duration.ofMillis(Math.max(1L, requestTimeoutMs));
        this.authorizationHeader = authorizationHeader;
        this.refreshIntervalMs = Math.max(1_000L, refreshIntervalMs);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(1L, connectTimeoutMs)))
                .version(HttpClient.Version.HTTP_1_1)
                .build();

        ThreadFactory tf = r -> {
            Thread t = Executors.defaultThreadFactory().newThread(r);
            t.setName("MarsLinker-acl-http-refresh");
            t.setDaemon(true);
            return t;
        };
        this.scheduler = Executors.newSingleThreadScheduledExecutor(tf);

        // 先立刻拉一次，再按间隔刷新
        safeRefreshOnce();
        this.scheduler.scheduleAtFixedRate(this::safeRefreshOnce, this.refreshIntervalMs, this.refreshIntervalMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public boolean allowsSubscribe(String topicFilter) {
        return delegate.get().allowsSubscribe(topicFilter);
    }

    @Override
    public boolean allowsPublish(String topic) {
        return delegate.get().allowsPublish(topic);
    }

    private void safeRefreshOnce() {
        try {
            AclRuleSet rules = fetchRuleSet();
            if (rules == null) {
                return;
            }
            PrefixAclProvider next = toPrefixAcl(rules);
            delegate.set(next);
            log.info("HTTP ACL 刷新成功 enabled={} defaultDeny={} allowSub={} allowPub={} denySub={} denyPub={}",
                    rules.enabled,
                    rules.defaultDeny,
                    sizeOf(rules.allowSubscribePrefixes),
                    sizeOf(rules.allowPublishPrefixes),
                    sizeOf(rules.denySubscribePrefixes),
                    sizeOf(rules.denyPublishPrefixes));
        } catch (Exception e) {
            log.warn("HTTP ACL 刷新失败 — {}", e.toString());
        }
    }

    private AclRuleSet fetchRuleSet() throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(endpoint)
                .GET()
                .timeout(requestTimeout)
                .header("Accept", "application/json");
        if (authorizationHeader != null && !authorizationHeader.isEmpty()) {
            b.header("Authorization", authorizationHeader);
        }
        HttpResponse<String> resp = httpClient.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        int code = resp.statusCode();
        if (code < 200 || code >= 300) {
            log.warn("HTTP ACL 拉取失败 status={}", code);
            return null;
        }
        String body = resp.body();
        if (body == null || body.isEmpty()) {
            log.warn("HTTP ACL 拉取响应为空，将忽略本次刷新");
            return null;
        }
        AclRuleSet rules = MAPPER.readValue(body, AclRuleSet.class);
        return rules == null ? null : rules.normalized();
    }

    private static PrefixAclProvider toPrefixAcl(AclRuleSet r) {
        boolean enabled = r.enabled == null ? true : r.enabled;
        boolean defaultDeny = r.defaultDeny == null ? false : r.defaultDeny;
        return new PrefixAclProvider(enabled,
                r.allowSubscribePrefixes,
                r.allowPublishPrefixes,
                defaultDeny,
                r.denySubscribePrefixes,
                r.denyPublishPrefixes);
    }

    private static int sizeOf(List<?> l) {
        return l == null ? 0 : l.size();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class AclRuleSet {
        public Boolean enabled;
        public Boolean defaultDeny;
        public List<String> allowSubscribePrefixes;
        public List<String> allowPublishPrefixes;
        public List<String> denySubscribePrefixes;
        public List<String> denyPublishPrefixes;

        AclRuleSet normalized() {
            AclRuleSet n = new AclRuleSet();
            n.enabled = enabled;
            n.defaultDeny = defaultDeny;
            n.allowSubscribePrefixes = normalizeList(allowSubscribePrefixes);
            n.allowPublishPrefixes = normalizeList(allowPublishPrefixes);
            n.denySubscribePrefixes = normalizeList(denySubscribePrefixes);
            n.denyPublishPrefixes = normalizeList(denyPublishPrefixes);
            return n;
        }

        private static List<String> normalizeList(List<String> v) {
            if (v == null || v.isEmpty()) {
                return Collections.emptyList();
            }
            v.removeIf(Objects::isNull);
            v.removeIf(String::isEmpty);
            return v;
        }
    }
}

