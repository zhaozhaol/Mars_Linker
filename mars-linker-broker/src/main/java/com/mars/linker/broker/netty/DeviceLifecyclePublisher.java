package com.mars.linker.broker.netty;

import io.netty.channel.ChannelHandlerContext;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * 设备上下线事件发布器。
 */
final class DeviceLifecyclePublisher {
    static final String DEVICE_CONNECTED_TOPIC = "devices/connected";
    static final String DEVICE_OFFLINE_TOPIC = "devices/offline";

    private final PublishCallback publishCallback;

    DeviceLifecyclePublisher(PublishCallback publishCallback) {
        this.publishCallback = publishCallback;
    }

    PublishResult publish(ChannelHandlerContext ctx,
                          String topic,
                          String event,
                          String clientId,
                          String reason) {
        if (clientId == null || clientId.isEmpty()) {
            return null;
        }
        Endpoint endpoint = endpointOf(ctx.channel().remoteAddress());
        String payload = "{"
                + "\"event\":\"" + jsonEscape(event) + "\","
                + "\"clientId\":\"" + jsonEscape(clientId) + "\","
                + "\"ip\":\"" + jsonEscape(endpoint.ip) + "\","
                + "\"port\":" + endpoint.port + ","
                + "\"reason\":\"" + jsonEscape(reason) + "\","
                + "\"ts\":" + System.currentTimeMillis()
                + "}";
        int delivered = publishCallback.publish(topic, payload.getBytes(StandardCharsets.UTF_8));
        return new PublishResult(endpoint.ip, endpoint.port, delivered);
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
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @FunctionalInterface
    interface PublishCallback {
        int publish(String topic, byte[] payload);
    }

    static final class PublishResult {
        final String ip;
        final int port;
        final int delivered;

        private PublishResult(String ip, int port, int delivered) {
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
