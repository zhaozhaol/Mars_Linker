package com.mars.linker.broker.ui.monitoring.model;

/**
 * 组件健康状态。
 */
public class ComponentHealth {

    /** 组件名称。 */
    private String name;
    /** 组件状态：UP / DOWN / DEGRADED。 */
    private String status;
    /** 状态详情描述。 */
    private String detail;

    public ComponentHealth() {}

    public ComponentHealth(String name, String status, String detail) {
        this.name = name;
        this.status = status;
        this.detail = detail;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
}
