package com.mars.linker.broker.netty.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 通过 HTTP 回调做 CONNECT 鉴权：向配置 URL 发送 JSON 负载，由远端返回 HTTP 状态码决定放行。
 * <p>
 * 成功条件：2xx 状态码（含 200/204 等）。其它状态码、超时与 IO 异常均视为鉴权失败。
 * </p>
 * <p>
 * 请求体为 UTF-8 JSON：{@code {"clientId":"...","username":...,"password":...}} ，字段与 MQTT
 * CONNECT 中解析结果一致，未带用户名或密码时对应字段为 {@code null} 的 JSON 字面量（不含引号的 {@code null}）。
 * </p>
 * <p>
 * 说明：在 Netty I/O 线程上同步执行 HTTP 调用，会阻塞该 worker；生产环境应控制回调时延、并发与探活。
 * </p>
 */
public final class HttpAuthProvider implements AuthProvider {
    private static final Logger log = LoggerFactory.getLogger(HttpAuthProvider.class);

    private final HttpClient httpClient;
    private final URI endpoint;
    private final Duration requestTimeout;
    private final String authorizationHeader;

    public HttpAuthProvider(String url, long connectTimeoutMs, long requestTimeoutMs, String authorizationHeader) {
        this.endpoint = URI.create(url);
        this.requestTimeout = Duration.ofMillis(Math.max(1L, requestTimeoutMs));
        this.authorizationHeader = authorizationHeader;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(1L, connectTimeoutMs)))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    @Override
    public boolean authenticate(String clientId, String username, String password) {
        if (clientId == null) {
            return false;
        }
        String body = buildJsonBody(clientId, username, password);
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(endpoint)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .timeout(requestTimeout)
                    .header("Content-Type", "application/json; charset=utf-8");
            if (authorizationHeader != null && !authorizationHeader.isEmpty()) {
                b.header("Authorization", authorizationHeader);
            }
            HttpResponse<String> resp = httpClient.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int code = resp.statusCode();
            if (code >= 200 && code < 300) {
                return true;
            }
            log.warn("HTTP 鉴权未通过 clientId={} status={}", clientId, code);
            return false;
        } catch (Exception e) {
            log.warn("HTTP 鉴权调用失败 clientId={} — {}", clientId, e.toString());
            return false;
        }
    }

    private static String buildJsonBody(String clientId, String username, String password) {
        return "{"
                + "\"clientId\":" + jsonStringOrNull(clientId) + ","
                + "\"username\":" + jsonStringOrNull(username) + ","
                + "\"password\":" + jsonStringOrNull(password)
                + "}";
    }

    private static String jsonStringOrNull(String s) {
        if (s == null) {
            return "null";
        }
        return '"' + jsonEscape(s) + '"';
    }

    private static String jsonEscape(String s) {
        StringBuilder b = new StringBuilder(s.length() + 8);
        for (int i = 0, n = s.length(); i < n; i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    b.append("\\\"");
                    break;
                case '\\':
                    b.append("\\\\");
                    break;
                case '\b':
                    b.append("\\b");
                    break;
                case '\f':
                    b.append("\\f");
                    break;
                case '\n':
                    b.append("\\n");
                    break;
                case '\r':
                    b.append("\\r");
                    break;
                case '\t':
                    b.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        b.append(String.format("\\u%04x", (int) c));
                    } else {
                        b.append(c);
                    }
            }
        }
        return b.toString();
    }
}
