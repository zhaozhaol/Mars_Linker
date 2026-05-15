package com.mars.linker.broker.config;

import com.mars.linker.broker.netty.protocol.EventForwardConfig;
import com.mars.linker.broker.netty.protocol.EventType;
import com.mars.linker.broker.netty.protocol.ForwardRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 事件通知配置校验器（启动时执行，校验失败拒绝启动）。
 * <p>
 * 职责：旧配置迁移 + 新配置校验。
 * </p>
 */
public final class LifecycleConfigValidator {

    private static final Logger log = LoggerFactory.getLogger(LifecycleConfigValidator.class);

    private LifecycleConfigValidator() {
    }

    public static void validate(MarsLinkerMqttBrokerProperties props) {
        migrateLegacyConfig(props);
        if (!props.isEventNotifyEnabled()) {
            return;
        }
        Map<String, EventForwardConfig> configMap = props.getEventNotifyConfig();
        if (configMap != null) {
            for (Map.Entry<String, EventForwardConfig> entry : configMap.entrySet()) {
                String configKey = entry.getKey();
                try {
                    EventType.fromConfigKey(configKey);
                } catch (IllegalArgumentException e) {
                    throw new IllegalStateException("event-notify-config 中不支持的事件类型: " + configKey);
                }
                EventForwardConfig cfg = entry.getValue();
                if (cfg.getTopic() != null) {
                    validateTopic(cfg.getTopic(), "event-notify-config." + configKey + ".topic");
                }
                validateForwardRules(cfg.getForwardRules(),
                        "event-notify-config." + configKey + ".forward-rules");
            }
        }
        validateForwardRules(props.getEventNotifyDefaultForwardRules(),
                "event-notify-default-forward-rules");
    }

    private static void migrateLegacyConfig(MarsLinkerMqttBrokerProperties props) {
        if (!props.isLifecycleNotifyEnabled()) {
            return;
        }
        if (props.isEventNotifyEnabled()) {
            log.warn("旧配置 lifecycle-notify-enabled=true 已废弃，因新配置 event-notify-enabled=true 已存在，旧配置将被忽略");
            return;
        }
        log.info("迁移旧配置：lifecycle-notify-enabled=true → event-notify-enabled=true");
        props.setEventNotifyEnabled(true);
        Map<String, EventForwardConfig> config = props.getEventNotifyConfig();
        if (config == null || config.isEmpty()) {
            config = new LinkedHashMap<>();
            props.setEventNotifyConfig(config);
        }
        EventForwardConfig connectedConfig = new EventForwardConfig();
        connectedConfig.setEnabled(true);
        connectedConfig.setTopic(props.getLifecycleConnectedTopic());
        config.put("connected", connectedConfig);
        EventForwardConfig disconnectedConfig = new EventForwardConfig();
        disconnectedConfig.setEnabled(true);
        disconnectedConfig.setTopic(props.getLifecycleOfflineTopic());
        config.put("disconnected", disconnectedConfig);
        if (props.getLifecycleForwardRules() != null && !props.getLifecycleForwardRules().isEmpty()) {
            props.setEventNotifyDefaultForwardRules(new ArrayList<>(props.getLifecycleForwardRules()));
        }
    }

    private static void validateTopic(String topic, String configName) {
        if (topic == null || topic.isEmpty()) {
            throw new IllegalStateException(configName + " 不能为空");
        }
        if (topic.indexOf('\0') >= 0) {
            throw new IllegalStateException(configName + " 包含非法空字符");
        }
        if (topic.getBytes(StandardCharsets.UTF_8).length > 65535) {
            throw new IllegalStateException(configName + " 长度超过 MQTT 主题上限 65535 字节");
        }
    }

    private static void validateForwardRules(List<ForwardRule> rules, String configName) {
        if (rules == null || rules.isEmpty()) {
            return;
        }
        for (int i = 0; i < rules.size(); i++) {
            ForwardRule rule = rules.get(i);
            if (rule.getMatchType() == null || !rule.getMatchType().equals("regex_match")) {
                throw new IllegalStateException(
                        configName + "[" + i + "].match-type 不支持: " + rule.getMatchType()
                                + "，当前仅支持 regex_match");
            }
            if (rule.getExpression() == null || rule.getExpression().isEmpty()) {
                throw new IllegalStateException(configName + "[" + i + "].expression 不能为空");
            }
            try {
                Pattern.compile(rule.getExpression());
            } catch (PatternSyntaxException e) {
                throw new IllegalStateException(
                        configName + "[" + i + "].expression 正则语法不合法: " + rule.getExpression()
                                + "，原因: " + e.getMessage());
            }
        }
    }
}
