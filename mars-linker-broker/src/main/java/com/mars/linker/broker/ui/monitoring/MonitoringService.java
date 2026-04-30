package com.mars.linker.broker.ui.monitoring;

import com.mars.linker.broker.config.MarsLinkerMqttBrokerProperties;
import com.mars.linker.broker.netty.MqttProtocolHandler;
import com.mars.linker.broker.ui.collection.DataCollectionService;
import com.mars.linker.broker.ui.config.MarsLinkerUiProperties;
import com.mars.linker.broker.ui.config.RuntimeConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Service
@ConditionalOnProperty(prefix = "mars.linker.ui", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MonitoringService {

    private static final Logger log = LoggerFactory.getLogger(MonitoringService.class);

    private final MarsLinkerMqttBrokerProperties brokerProperties;
    private final RuntimeConfigService runtimeConfigService;
    private final DataCollectionService dataCollectionService;
    private final MarsLinkerUiProperties uiProperties;

    private final AtomicLong connectionsPeakTotal = new AtomicLong(0);

    private volatile long lastPublishInTotal = 0;
    private volatile long lastPublishOutTotal = 0;
    private volatile double publishInRate = 0.0;
    private volatile double publishOutRate = 0.0;

    private volatile boolean initialized = true;

    public MonitoringService(MarsLinkerMqttBrokerProperties brokerProperties,
                             RuntimeConfigService runtimeConfigService,
                             DataCollectionService dataCollectionService,
                             MarsLinkerUiProperties uiProperties) {
        this.brokerProperties = brokerProperties;
        this.runtimeConfigService = runtimeConfigService;
        this.dataCollectionService = dataCollectionService;
        this.uiProperties = uiProperties;
    }

    @PostConstruct
    void init() {
        try {
            MqttProtocolHandler.metricConnectionsActive();
            log.info("MonitoringService initialized successfully");
        } catch (Throwable t) {
            initialized = false;
            log.warn("MonitoringService initialization failed: {}", t.getMessage());
        }
    }

    public Map<String, Object> overview() {
        if (!initialized) {
            throw new ServiceUnavailableException("Monitoring service not initialized");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("timestamp", Instant.now().toEpochMilli());
        data.put("broker", brokerOverview());
        data.put("metrics", metricOverview());
        data.put("runtimeConfig", runtimeConfigService.snapshot());
        data.put("collection", collectionOverview());
        return data;
    }

    public void updateConnectionsPeak(long currentActive) {
        connectionsPeakTotal.accumulateAndGet(currentActive, Math::max);
    }

    public void calculateMessageRates(long refreshMs) {
        long currentIn = MqttProtocolHandler.metricPublishInTotal();
        long currentOut = MqttProtocolHandler.metricPublishOutTotal();
        if (refreshMs > 0) {
            double seconds = refreshMs / 1000.0;
            publishInRate = (currentIn - lastPublishInTotal) / seconds;
            publishOutRate = (currentOut - lastPublishOutTotal) / seconds;
        }
        lastPublishInTotal = currentIn;
        lastPublishOutTotal = currentOut;
    }

    public long getConnectionsPeakTotal() {
        return connectionsPeakTotal.get();
    }

    public double getPublishInRate() {
        return publishInRate;
    }

    public double getPublishOutRate() {
        return publishOutRate;
    }

    public boolean isInitialized() {
        return initialized;
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
        long active = MqttProtocolHandler.metricConnectionsActive();
        updateConnectionsPeak(active);
        metrics.put("connectionsActive", active);
        metrics.put("connectionsPeakTotal", connectionsPeakTotal.get());
        metrics.put("connectAcceptedTotal", MqttProtocolHandler.metricConnectAcceptedTotal());
        metrics.put("connectRejectedTotal", MqttProtocolHandler.metricConnectRejectedTotal());
        metrics.put("publishInTotal", MqttProtocolHandler.metricPublishInTotal());
        metrics.put("publishOutTotal", MqttProtocolHandler.metricPublishOutTotal());
        metrics.put("publishInRate", publishInRate);
        metrics.put("publishOutRate", publishOutRate);
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
