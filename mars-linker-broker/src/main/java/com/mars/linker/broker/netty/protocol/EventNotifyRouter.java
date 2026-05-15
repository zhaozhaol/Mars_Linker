package com.mars.linker.broker.netty.protocol;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 事件通知路由决策引擎。
 * <p>
 * 封装了事件通知的完整决策链：
 * 总开关 → 事件类型启用判断 → 规则匹配 → 主题解析
 * </p>
 * <p>
 * <b>核心特性</b>：
 * <ul>
 *   <li>零开销：总开关关闭时仅一次布尔判断即返回 null</li>
 *   <li>事件类型粒度控制：每种事件类型独立开关、独立主题、独立规则</li>
 *   <li>规则三态继承：forwardRules=null→继承全局默认规则；forwardRules=[]→全量匹配（不继承）；forwardRules非空→按独立规则匹配（不继承）</li>
 *   <li>正则编译缓存：每个 ForwardRuleMatcher 在构造时一次性编译正则</li>
 * </ul>
 * </p>
 */
public final class EventNotifyRouter {

    private final boolean eventNotifyEnabled;
    private final Map<EventType, ResolvedEventConfig> resolvedConfigs;
    private final ForwardRuleMatcher defaultRuleMatcher;

    public EventNotifyRouter(boolean eventNotifyEnabled,
                             Map<String, EventForwardConfig> configMap,
                             List<ForwardRule> defaultRules) {
        this.eventNotifyEnabled = eventNotifyEnabled;
        this.defaultRuleMatcher = new ForwardRuleMatcher(
                defaultRules != null ? defaultRules : Collections.emptyList());
        Map<EventType, ResolvedEventConfig> configs = new EnumMap<>(EventType.class);
        if (configMap != null) {
            for (Map.Entry<String, EventForwardConfig> entry : configMap.entrySet()) {
                EventType eventType = EventType.fromConfigKey(entry.getKey());
                EventForwardConfig cfg = entry.getValue();
                String topic = cfg.getTopic() != null ? cfg.getTopic() : eventType.getDefaultTopic();
                ForwardRuleMatcher matcher = cfg.getForwardRules() != null
                        ? new ForwardRuleMatcher(cfg.getForwardRules())
                        : null;
                configs.put(eventType, new ResolvedEventConfig(cfg.isEnabled(), topic, matcher));
            }
        }
        this.resolvedConfigs = Collections.unmodifiableMap(configs);
    }

    /**
     * 路由决策：判断某事件类型是否应转发，若应转发则返回目标主题。
     *
     * @param eventType 事件类型枚举
     * @param clientId  设备客户端标识
     * @return 转发目标主题（应转发）；null（不应转发）
     */
    public String shouldNotify(EventType eventType, String clientId) {
        if (!eventNotifyEnabled) {
            return null;
        }
        ResolvedEventConfig config = resolvedConfigs.get(eventType);
        if (config == null || !config.enabled) {
            return null;
        }
        ForwardRuleMatcher matcher = config.ruleMatcher != null
                ? config.ruleMatcher
                : defaultRuleMatcher;
        if (!matcher.matches(clientId)) {
            return null;
        }
        return config.topic;
    }

    public boolean isEventNotifyEnabled() {
        return eventNotifyEnabled;
    }

    public ForwardRuleMatcher getRuleMatcher(EventType eventType) {
        ResolvedEventConfig config = resolvedConfigs.get(eventType);
        if (config == null) {
            return null;
        }
        return config.ruleMatcher != null ? config.ruleMatcher : defaultRuleMatcher;
    }

    private static final class ResolvedEventConfig {
        final boolean enabled;
        final String topic;
        final ForwardRuleMatcher ruleMatcher;

        ResolvedEventConfig(boolean enabled, String topic, ForwardRuleMatcher ruleMatcher) {
            this.enabled = enabled;
            this.topic = topic;
            this.ruleMatcher = ruleMatcher;
        }
    }
}
