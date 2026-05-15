package com.mars.linker.broker.netty.protocol;

import java.util.List;

/**
 * 单个事件类型的转发配置模型。
 * <p>
 * 每种事件类型可独立配置是否启用转发、转发目标主题和转发规则。
 * <ul>
 *   <li>topic 为 null 时使用 EventType 中的默认主题</li>
 *   <li>forwardRules 为 null 时继承全局默认转发规则</li>
 *   <li>forwardRules 为空列表时不继承全局默认规则，全量匹配</li>
 *   <li>forwardRules 为非空列表时不继承全局默认规则，按独立规则匹配</li>
 * </ul>
 * </p>
 */
public class EventForwardConfig {

    private boolean enabled = true;
    private String topic;
    private List<ForwardRule> forwardRules;

    public EventForwardConfig() {
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public List<ForwardRule> getForwardRules() {
        return forwardRules;
    }

    public void setForwardRules(List<ForwardRule> forwardRules) {
        this.forwardRules = forwardRules;
    }
}
