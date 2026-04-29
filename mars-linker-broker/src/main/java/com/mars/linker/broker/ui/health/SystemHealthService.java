package com.mars.linker.broker.ui.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.lang.management.*;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class SystemHealthService {

    private static final Logger log = LoggerFactory.getLogger(SystemHealthService.class);

    public Map<String, Object> collect() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("timestamp", System.currentTimeMillis());
        data.put("heap", collectHeap());
        data.put("gc", collectGc());
        data.put("threads", collectThreads());
        data.put("cpu", collectCpu());
        data.put("disk", collectDisk());
        return data;
    }

    private Map<String, Object> collectHeap() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memoryBean.getHeapMemoryUsage();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("used", heap.getUsed());
        m.put("max", heap.getMax());
        m.put("committed", heap.getCommitted());
        m.put("usagePercent", heap.getMax() > 0 ? round(heap.getUsed() * 100.0 / heap.getMax()) : 0);
        return m;
    }

    private Map<String, Object> collectGc() {
        Map<String, Object> m = new LinkedHashMap<>();
        long youngCount = 0, youngTime = 0, oldCount = 0, oldTime = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            String name = gc.getName().toLowerCase();
            long count = gc.getCollectionCount() < 0 ? 0 : gc.getCollectionCount();
            long time = gc.getCollectionTime() < 0 ? 0 : gc.getCollectionTime();
            if (name.contains("young") || name.contains("minor") || name.contains("parnew") || name.contains("ps marksweep") && name.contains("young")) {
                youngCount += count;
                youngTime += time;
            } else {
                oldCount += count;
                oldTime += time;
            }
        }
        m.put("youngGcCount", youngCount);
        m.put("youngGcTimeMs", youngTime);
        m.put("fullGcCount", oldCount);
        m.put("fullGcTimeMs", oldTime);
        return m;
    }

    private Map<String, Object> collectThreads() {
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("active", threadBean.getThreadCount());
        m.put("peak", threadBean.getPeakThreadCount());
        m.put("daemon", threadBean.getDaemonThreadCount());
        m.put("totalStarted", threadBean.getTotalStartedThreadCount());
        return m;
    }

    private Map<String, Object> collectCpu() {
        Map<String, Object> m = new LinkedHashMap<>();
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        m.put("availableProcessors", osBean.getAvailableProcessors());
        m.put("systemLoadAverage", osBean.getSystemLoadAverage());
        try {
            if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
                com.sun.management.OperatingSystemMXBean sunOs = (com.sun.management.OperatingSystemMXBean) osBean;
                double procCpu = sunOs.getProcessCpuLoad();
                double sysCpu = sunOs.getSystemCpuLoad();
                m.put("processCpuPercent", procCpu < 0 ? 0 : round(procCpu * 100));
                m.put("systemCpuPercent", sysCpu < 0 ? 0 : round(sysCpu * 100));
            }
        } catch (Exception e) {
            log.debug("CPU detail unavailable: {}", e.getMessage());
        }
        return m;
    }

    private Map<String, Object> collectDisk() {
        File root = new File("/");
        Map<String, Object> m = new LinkedHashMap<>();
        long total = root.getTotalSpace();
        long free = root.getFreeSpace();
        long usable = root.getUsableSpace();
        long used = total - free;
        m.put("total", total);
        m.put("used", used);
        m.put("free", free);
        m.put("usable", usable);
        m.put("usagePercent", total > 0 ? round(used * 100.0 / total) : 0);
        return m;
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
