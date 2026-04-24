package com.mars.linker.broker.netty;

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * 将 Broker 运行时指标注册到 Micrometer（Prometheus）。
 * <p>
 * 约定：瞬时值用 {@link Gauge}（如 active connections），累计值用 {@link FunctionCounter}（如 total accepts）。
 * </p>
 * <p>
 * 对接建议：联调阶段优先关注 {@code nexus_mqtt_connect_rejected_total} 与
 * {@code nexus_mqtt_acl_publish_deny_total}，可快速定位“账号配置不一致”与“主题 ACL 未放通”问题。
 * </p>
 */
@Component
public class MqttBrokerMetricsBinder {

    public MqttBrokerMetricsBinder(MeterRegistry meterRegistry) {
        Gauge.builder("nexus_mqtt_connections_active", MqttProtocolHandler::metricConnectionsActive)
                .description("Current active MQTT TCP connections")
                .register(meterRegistry);
        FunctionCounter.builder("nexus_mqtt_connect_accepted_total", this,
                        c -> MqttProtocolHandler.metricConnectAcceptedTotal())
                .description("Total accepted CONNECT packets")
                .register(meterRegistry);
        FunctionCounter.builder("nexus_mqtt_connect_rejected_total", this,
                        c -> MqttProtocolHandler.metricConnectRejectedTotal())
                .description("Total rejected CONNECT packets")
                .register(meterRegistry);
        FunctionCounter.builder("nexus_mqtt_publish_in_total", this,
                        c -> MqttProtocolHandler.metricPublishInTotal())
                .description("Total inbound PUBLISH packets")
                .register(meterRegistry);
        FunctionCounter.builder("nexus_mqtt_publish_out_total", this,
                        c -> MqttProtocolHandler.metricPublishOutTotal())
                .description("Total fan-out deliveries to subscribers")
                .register(meterRegistry);
        Gauge.builder("nexus_mqtt_qos2_in_pending", MqttProtocolHandler::metricQos2InPending)
                .description("Current pending inbound QoS2 messages awaiting PUBREL")
                .register(meterRegistry);
        FunctionCounter.builder("nexus_mqtt_qos2_in_completed_total", this,
                        c -> MqttProtocolHandler.metricQos2InCompletedTotal())
                .description("Total completed inbound QoS2 handshakes")
                .register(meterRegistry);
        FunctionCounter.builder("nexus_mqtt_acl_subscribe_deny_total", this,
                        c -> MqttProtocolHandler.metricAclSubscribeDenyTotal())
                .description("Total ACL denied SUBSCRIBE operations")
                .register(meterRegistry);
        FunctionCounter.builder("nexus_mqtt_acl_publish_deny_total", this,
                        c -> MqttProtocolHandler.metricAclPublishDenyTotal())
                .description("Total ACL denied PUBLISH operations")
                .register(meterRegistry);
    }
}
