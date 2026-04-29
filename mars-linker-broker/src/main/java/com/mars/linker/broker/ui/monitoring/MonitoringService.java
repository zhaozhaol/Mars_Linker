package com.mars.linker.broker.ui.monitoring;

import com.mars.linker.broker.config.MarsLinkerMqttBrokerProperties;
import com.mars.linker.broker.netty.MqttProtocolHandler;
import com.mars.linker.broker.ui.collection.DataCollectionService;
import com.mars.linker.broker.ui.config.RuntimeConfigService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 聚合界面监控数据。
 */
@Service
public class MonitoringService {

    private final MarsLinkerMqttBrokerProperties brokerProperties;
    private final RuntimeConfigService runtimeConfigService;
    private final DataCollectionService dataCollectionService;

    public MonitoringService(MarsLinkerMqttBrokerProperties brokerProperties,
                             RuntimeConfigService runtimeConfigService,
                             DataCollectionService dataCollectionService) {
        this.brokerProperties = brokerProperties;
        this.runtimeConfigService = runtimeConfigService;
        this.dataCollectionService = dataCollectionService;
    }

    public Map<String, Object> overview() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("timestamp", Instant.now().toEpochMilli());
        data.put("broker", brokerOverview());
        data.put("metrics", metricOverview());
        data.put("runtimeConfig", runtimeConfigService.snapshot());
        data.put("collection", collectionOverview());
        return data;
    }

    private Map<String, Object> brokerOverview() {
        Map<String, Object> broker = new LinkedHashMap<>();
        broker.put("nettyEnabled", brokerProperties.isNettyEnabled());
        broker.put("tcpPort", brokerProperties.getTcpPort());
        broker.put("storageEnabled", brokerProperties.isStorageEnabled());
        broker.put("storageMode", brokerProperties.getStorageMode());
        return broker;
    }

    private Map<String, Object> metricOverview() {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("connectionsActive", MqttProtocolHandler.metricConnectionsActive());
        metrics.put("connectAcceptedTotal", MqttProtocolHandler.metricConnectAcceptedTotal());
        metrics.put("connectRejectedTotal", MqttProtocolHandler.metricConnectRejectedTotal());
        metrics.put("publishInTotal", MqttProtocolHandler.metricPublishInTotal());
        metrics.put("publishOutTotal", MqttProtocolHandler.metricPublishOutTotal());
        metrics.put("qos2InPending", MqttProtocolHandler.metricQos2InPending());
        metrics.put("qos2InCompletedTotal", MqttProtocolHandler.metricQos2InCompletedTotal());
        metrics.put("aclSubscribeDenyTotal", MqttProtocolHandler.metricAclSubscribeDenyTotal());
        metrics.put("aclPublishDenyTotal", MqttProtocolHandler.metricAclPublishDenyTotal());
        return metrics;
    }

    private Map<String, Object> collectionOverview() {
        Map<String, Object> collection = new LinkedHashMap<>();
        collection.put("bufferedSize", dataCollectionService.bufferedSize());
        collection.put("totalCollected", dataCollectionService.totalCollected());
        return collection;
    }
}
