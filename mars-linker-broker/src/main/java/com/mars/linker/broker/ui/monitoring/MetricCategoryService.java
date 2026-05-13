package com.mars.linker.broker.ui.monitoring;

import com.mars.linker.broker.netty.MqttProtocolHandler;
import com.mars.linker.broker.ui.config.MarsLinkerUiProperties;
import com.mars.linker.broker.ui.health.SystemHealthService;
import com.mars.linker.broker.ui.monitoring.isolation.MonitoringFaultBoundary;
import com.mars.linker.broker.ui.monitoring.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.lang.management.*;
import java.util.ArrayList;
import java.util.List;

@Service
@ConditionalOnProperty(prefix = "mars.linker.ui", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MetricCategoryService {

    private static final Logger log = LoggerFactory.getLogger(MetricCategoryService.class);
    private final MarsLinkerUiProperties uiProperties;
    private final MonitoringService monitoringService;
    private final SystemHealthService systemHealthService;
    private final MqttProtocolHandler protocolHandler;

    public MetricCategoryService(MarsLinkerUiProperties uiProperties,
                                 MonitoringService monitoringService,
                                 SystemHealthService systemHealthService,
                                 MqttProtocolHandler protocolHandler) {
        this.uiProperties = uiProperties;
        this.monitoringService = monitoringService;
        this.systemHealthService = systemHealthService;
        this.protocolHandler = protocolHandler;
    }

    public ConnectionMetrics connectionMetrics() {
        if (!uiProperties.isConnectionEnabled()) {
            return new ConnectionMetrics(System.currentTimeMillis(), 0, 0, 0, 0);
        }
        return MonitoringFaultBoundary.executeWithResult(() -> {
            long now = System.currentTimeMillis();
            long active = MqttProtocolHandler.metricConnectionsActive();
            monitoringService.updateConnectionsPeak(active);
            return new ConnectionMetrics(now, active,
                    monitoringService.getConnectionsPeakTotal(),
                    MqttProtocolHandler.metricConnectAcceptedTotal(),
                    MqttProtocolHandler.metricConnectRejectedTotal());
        }, new ConnectionMetrics());
    }

    public MessageMetrics messageMetrics() {
        if (!uiProperties.isMessageEnabled()) {
            return new MessageMetrics(System.currentTimeMillis(), 0, 0, 0, 0);
        }
        return MonitoringFaultBoundary.executeWithResult(() -> {
            long now = System.currentTimeMillis();
            return new MessageMetrics(now,
                    MqttProtocolHandler.metricPublishInTotal(),
                    MqttProtocolHandler.metricPublishOutTotal(),
                    monitoringService.getPublishInRate(),
                    monitoringService.getPublishOutRate());
        }, new MessageMetrics());
    }

    public SubscriptionMetrics subscriptionMetrics() {
        if (!uiProperties.isSubscriptionEnabled()) {
            return new SubscriptionMetrics(System.currentTimeMillis(), 0, 0, 0, false);
        }
        return MonitoringFaultBoundary.executeWithResult(() -> {
            long now = System.currentTimeMillis();
            try {
                var registry = protocolHandler.subscriptionRegistry();
                return new SubscriptionMetrics(now,
                        registry.subscriptionTotal(),
                        registry.topicCount(),
                        registry.treeDepth(),
                        false);
            } catch (Exception e) {
                return new SubscriptionMetrics(now, 0, 0, 0, true);
            }
        }, new SubscriptionMetrics(System.currentTimeMillis(), 0, 0, 0, true));
    }

    public SystemMetrics systemMetrics() {
        if (!uiProperties.isSystemEnabled()) {
            return new SystemMetrics(System.currentTimeMillis(), 0, 0, 0, 0, 0);
        }
        return MonitoringFaultBoundary.executeWithResult(() -> {
            long now = System.currentTimeMillis();
            MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();
            MemoryUsage heap = memBean.getHeapMemoryUsage();
            double heapUsedRatio = heap.getMax() > 0 ? (double) heap.getUsed() / heap.getMax() : 0;

            double cpuUsage = 0;
            try {
                OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
                if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
                    double procCpu = ((com.sun.management.OperatingSystemMXBean) osBean).getProcessCpuLoad();
                    cpuUsage = procCpu < 0 ? 0 : procCpu;
                }
            } catch (Exception e) {
                log.debug("CPU metric unavailable: {}", e.getMessage());
            }

            ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
            int threadCount = threadBean.getThreadCount();

            long gcCount = 0, gcTime = 0;
            for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
                gcCount += Math.max(0, gc.getCollectionCount());
                gcTime += Math.max(0, gc.getCollectionTime());
            }

            return new SystemMetrics(now, heapUsedRatio, cpuUsage, threadCount, gcCount, gcTime);
        }, new SystemMetrics());
    }

    public HealthStatus healthStatus() {
        return MonitoringFaultBoundary.executeWithResult(() -> {
            long now = System.currentTimeMillis();
            List<ComponentHealth> components = new ArrayList<>();
            String overallStatus = "UP";

            long activeConns = MqttProtocolHandler.metricConnectionsActive();
            components.add(new ComponentHealth("mqtt", "UP", "Active connections: " + activeConns));

            MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();
            MemoryUsage heap = memBean.getHeapMemoryUsage();
            double heapRatio = heap.getMax() > 0 ? (double) heap.getUsed() / heap.getMax() : 0;
            String heapStatus = heapRatio > 0.9 ? "DOWN" : heapRatio > 0.8 ? "DEGRADED" : "UP";
            components.add(new ComponentHealth("heap", heapStatus,
                    String.format("Heap usage: %.1f%%", heapRatio * 100)));
            if ("DOWN".equals(heapStatus)) overallStatus = "DOWN";
            else if ("DEGRADED".equals(heapStatus) && "UP".equals(overallStatus)) overallStatus = "DEGRADED";

            components.add(new ComponentHealth("storage", "UP", "Storage mode: file"));
            return new HealthStatus(now, overallStatus, components);
        }, new HealthStatus(System.currentTimeMillis(), "DOWN", new ArrayList<>()));
    }
}
