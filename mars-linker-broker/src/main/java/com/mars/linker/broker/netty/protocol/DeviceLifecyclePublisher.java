package com.mars.linker.broker.netty.protocol;

import io.netty.channel.ChannelHandlerContext;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;

/**
 * 设备事件发布器（重构后）。
 * <p>
 * <b>变更点</b>：
 * <ul>
 *   <li>新增 publish() 重载方法，支持扩展字段 Map</li>
 *   <li>原 publish() 方法保持兼容（内部调用新方法，扩展字段为空）</li>
 *   <li>JSON 载荷格式：公共字段 + 事件特有扩展字段</li>
 * </ul>
 * </p>
 */
public final class DeviceLifecyclePublisher {

    private final PublishCallback publishCallback;

    public DeviceLifecyclePublisher(PublishCallback publishCallback) {
        this.publishCallback = publishCallback;
    }

    public PublishResult publish(ChannelHandlerContext ctx,
                                 String topic,
                                 String event,
                                 String clientId,
                                 String reason) {
        return publish(ctx, topic, event, clientId, reason, null);
    }

    public PublishResult publish(ChannelHandlerContext ctx,
                                 String topic,
                                 String event,
                                 String clientId,
                                 String reason,
                                 Map<String, Object> extensions) {
        if (clientId == null || clientId.isEmpty()) {
            return null;
        }
        Endpoint endpoint = endpointOf(ctx.channel().remoteAddress());
        StringBuilder json = new StringBuilder(128);
        json.append("{");
        json.append("\"event\":\"").append(jsonEscape(event)).append("\",");
        json.append("\"clientId\":\"").append(jsonEscape(clientId)).append("\",");
        json.append("\"ip\":\"").append(jsonEscape(endpoint.ip)).append("\",");
        json.append("\"port\":").append(endpoint.port).append(",");
        json.append("\"reason\":\"").append(jsonEscape(reason)).append("\",");
        if (extensions != null && !extensions.isEmpty()) {
            for (Map.Entry<String, Object> entry : extensions.entrySet()) {
                json.append("\"").append(jsonEscape(entry.getKey())).append("\":");
                appendJsonValue(json, entry.getValue());
                json.append(",");
            }
        }
        json.append("\"ts\":").append(System.currentTimeMillis());
        json.append("}");
        int delivered = publishCallback.publish(topic, json.toString().getBytes(StandardCharsets.UTF_8));
        return new PublishResult(endpoint.ip, endpoint.port, delivered);
    }

    private static void appendJsonValue(StringBuilder json, Object value) {
        if (value == null) {
            json.append("null");
        } else if (value instanceof Number) {
            json.append(value);
        } else if (value instanceof Boolean) {
            json.append(value);
        } else if (value instanceof Collection) {
            json.append("[");
            boolean first = true;
            for (Object item : (Collection<?>) value) {
                if (!first) {
                    json.append(",");
                }
                appendJsonValue(json, item);
                first = false;
            }
            json.append("]");
        } else {
            json.append("\"").append(jsonEscape(value.toString())).append("\"");
        }
    }

    private static Endpoint endpointOf(SocketAddress remoteAddress) {
        if (remoteAddress instanceof InetSocketAddress) {
            InetSocketAddress inet = (InetSocketAddress) remoteAddress;
            String ip = inet.getAddress() != null ? inet.getAddress().getHostAddress() : inet.getHostString();
            return new Endpoint(ip == null ? "unknown" : ip, inet.getPort());
        }
        if (remoteAddress == null) {
            return new Endpoint("unknown", -1);
        }
        return new Endpoint(remoteAddress.toString(), -1);
    }

    private static String jsonEscape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '"':  sb.append("\\\""); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                    break;
            }
        }
        return sb.toString();
    }

    @FunctionalInterface
    public interface PublishCallback {
        int publish(String topic, byte[] payload);
    }

    public static final class PublishResult {
        public final String ip;
        public final int port;
        public final int delivered;

        public PublishResult(String ip, int port, int delivered) {
            this.ip = ip;
            this.port = port;
            this.delivered = delivered;
        }
    }

    private static final class Endpoint {
        final String ip;
        final int port;

        private Endpoint(String ip, int port) {
            this.ip = ip;
            this.port = port;
        }
    }
}
