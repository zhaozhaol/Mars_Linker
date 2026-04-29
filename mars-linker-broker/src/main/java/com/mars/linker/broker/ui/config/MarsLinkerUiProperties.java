package com.mars.linker.broker.ui.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * UI 管理模块配置。
 */
@Component
@ConfigurationProperties(prefix = "mars.linker.ui")
public class MarsLinkerUiProperties {

    /**
     * 是否启用 UI 管理模块 API。
     */
    private boolean enabled = true;

    /**
     * 数据采集缓冲区大小。
     */
    private int collectionBufferSize = 1_000;

    /**
     * 监控页默认刷新间隔（毫秒）。
     */
    private long monitorRefreshMs = 5_000L;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getCollectionBufferSize() {
        return collectionBufferSize;
    }

    public void setCollectionBufferSize(int collectionBufferSize) {
        this.collectionBufferSize = collectionBufferSize;
    }

    public long getMonitorRefreshMs() {
        return monitorRefreshMs;
    }

    public void setMonitorRefreshMs(long monitorRefreshMs) {
        this.monitorRefreshMs = monitorRefreshMs;
    }
}
