//package com.mars.linker.broker.config;
//
//import com.mars.linker.broker.netty.protocol.EventForwardConfig;
//import com.mars.linker.broker.netty.protocol.ForwardRule;
//import org.junit.jupiter.api.Test;
//
//import java.util.Arrays;
//import java.util.Collections;
//import java.util.LinkedHashMap;
//import java.util.List;
//import java.util.Map;
//
//import static org.junit.jupiter.api.Assertions.*;
//
//class LifecycleConfigValidatorTest {
//
//    private MarsLinkerMqttBrokerProperties createProps(boolean enabled,
//                                                       Map<String, EventForwardConfig> config,
//                                                       List<ForwardRule> defaultRules) {
//        MarsLinkerMqttBrokerProperties props = new MarsLinkerMqttBrokerProperties();
//        props.setEventNotifyEnabled(enabled);
//        props.setEventNotifyConfig(config);
//        props.setEventNotifyDefaultForwardRules(defaultRules);
//        return props;
//    }
//
//    @Test
//    void testDisabledSkipsValidation() {
//        MarsLinkerMqttBrokerProperties props = createProps(false, null, null);
//        assertDoesNotThrow(() -> LifecycleConfigValidator.validate(props));
//    }
//
//    @Test
//    void testValidConfigPasses() {
//        Map<String, EventForwardConfig> config = new LinkedHashMap<>();
//        config.put("connected", new EventForwardConfig());
//        config.put("disconnected", new EventForwardConfig());
//        MarsLinkerMqttBrokerProperties props = createProps(true, config, Collections.emptyList());
//        assertDoesNotThrow(() -> LifecycleConfigValidator.validate(props));
//    }
//
//    @Test
//    void testValidConfigWithRulesPasses() {
//        EventForwardConfig cfg = new EventForwardConfig();
//        cfg.setForwardRules(Collections.singletonList(new ForwardRule("regex_match", "device")));
//        Map<String, EventForwardConfig> config = new LinkedHashMap<>();
//        config.put("connected", cfg);
//        MarsLinkerMqttBrokerProperties props = createProps(true, config, Collections.emptyList());
//        assertDoesNotThrow(() -> LifecycleConfigValidator.validate(props));
//    }
//
//    @Test
//    void testUnsupportedEventTypeThrows() {
//        Map<String, EventForwardConfig> config = new LinkedHashMap<>();
//        config.put("unknown-type", new EventForwardConfig());
//        MarsLinkerMqttBrokerProperties props = createProps(true, config, Collections.emptyList());
//        assertThrows(IllegalStateException.class, () -> LifecycleConfigValidator.validate(props));
//    }
//
//    @Test
//    void testEmptyTopicInEventConfigThrows() {
//        EventForwardConfig cfg = new EventForwardConfig();
//        cfg.setTopic("");
//        Map<String, EventForwardConfig> config = new LinkedHashMap<>();
//        config.put("connected", cfg);
//        MarsLinkerMqttBrokerProperties props = createProps(true, config, Collections.emptyList());
//        assertThrows(IllegalStateException.class, () -> LifecycleConfigValidator.validate(props));
//    }
//
//    @Test
//    void testNullCharInTopicThrows() {
//        EventForwardConfig cfg = new EventForwardConfig();
//        cfg.setTopic("devices\0/connected");
//        Map<String, EventForwardConfig> config = new LinkedHashMap<>();
//        config.put("connected", cfg);
//        MarsLinkerMqttBrokerProperties props = createProps(true, config, Collections.emptyList());
//        assertThrows(IllegalStateException.class, () -> LifecycleConfigValidator.validate(props));
//    }
//
//    @Test
//    void testInvalidMatchTypeInEventConfigThrows() {
//        EventForwardConfig cfg = new EventForwardConfig();
//        cfg.setForwardRules(Collections.singletonList(new ForwardRule("prefix_match", "dev/")));
//        Map<String, EventForwardConfig> config = new LinkedHashMap<>();
//        config.put("connected", cfg);
//        MarsLinkerMqttBrokerProperties props = createProps(true, config, Collections.emptyList());
//        IllegalStateException ex = assertThrows(IllegalStateException.class,
//                () -> LifecycleConfigValidator.validate(props));
//        assertTrue(ex.getMessage().contains("regex_match"));
//    }
//
//    @Test
//    void testInvalidRegexInDefaultRulesThrows() {
//        List<ForwardRule> defaultRules = Collections.singletonList(new ForwardRule("regex_match", "[invalid"));
//        MarsLinkerMqttBrokerProperties props = createProps(true, Collections.emptyMap(), defaultRules);
//        assertThrows(IllegalStateException.class, () -> LifecycleConfigValidator.validate(props));
//    }
//
//    @Test
//    void testLegacyConfigMigration() {
//        MarsLinkerMqttBrokerProperties props = new MarsLinkerMqttBrokerProperties();
//        props.setLifecycleNotifyEnabled(true);
//        props.setLifecycleConnectedTopic("custom/online");
//        props.setLifecycleOfflineTopic("custom/offline");
//        props.setLifecycleForwardRules(Collections.singletonList(new ForwardRule("regex_match", "device")));
//        assertDoesNotThrow(() -> LifecycleConfigValidator.validate(props));
//        assertTrue(props.isEventNotifyEnabled());
//        assertNotNull(props.getEventNotifyConfig());
//        assertTrue(props.getEventNotifyConfig().containsKey("connected"));
//        assertTrue(props.getEventNotifyConfig().containsKey("disconnected"));
//    }
//
//    @Test
//    void testLegacyConfigIgnoredWhenNewConfigPresent() {
//        MarsLinkerMqttBrokerProperties props = new MarsLinkerMqttBrokerProperties();
//        props.setEventNotifyEnabled(false);
//        props.setLifecycleNotifyEnabled(true);
//        LifecycleConfigValidator.validate(props);
//        assertFalse(props.isEventNotifyEnabled());
//    }
//}
