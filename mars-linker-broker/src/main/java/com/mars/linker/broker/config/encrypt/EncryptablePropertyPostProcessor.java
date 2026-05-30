package com.mars.linker.broker.config.encrypt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

/**
 * Spring Environment 后置处理器：在 Spring Environment 初始化阶段扫描所有配置值，
 * 对 ENC() 包裹的值透明解密。
 * <p>
 * 密钥来源优先级：
 * 1. 环境变量 MARS_CONFIG_ENCRYPTION_KEY
 * 2. 配置项 mars.linker.broker.config-encryption-key
 * </p>
 * <p>
 * 无密钥时跳过解密（向后兼容），有密钥但解密失败时启动失败。
 * </p>
 */
public class EncryptablePropertyPostProcessor implements EnvironmentPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(EncryptablePropertyPostProcessor.class);
    private static final String ENV_KEY = "MARS_CONFIG_ENCRYPTION_KEY";
    private static final String CONFIG_KEY = "mars.linker.broker.config-encryption-key";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String encryptionKey = resolveEncryptionKey(environment);
        if (encryptionKey == null || encryptionKey.isEmpty()) {
            log.debug("未配置加密密钥，跳过配置解密");
            return;
        }

        ConfigDecryptor decryptor = new ConfigDecryptor(encryptionKey);
        Map<String, Object> decrypted = new HashMap<>();

        for (var propertySource : environment.getPropertySources()) {
            if (!(propertySource instanceof EnumerablePropertySource)) {
                continue;
            }
            EnumerablePropertySource<?> eps = (EnumerablePropertySource<?>) propertySource;
            for (String propertyName : eps.getPropertyNames()) {
                Object value = eps.getProperty(propertyName);
                if (value instanceof String && ((String) value).startsWith("ENC(")) {
                    try {
                        String decryptedValue = decryptor.decryptIfEncrypted((String) value);
                        decrypted.put(propertyName, decryptedValue);
                        log.debug("配置项 {} 已解密", propertyName);
                    } catch (Exception e) {
                        log.error("配置项 {} 解密失败: {}", propertyName, e.getMessage());
                        throw e;
                    }
                }
            }
        }

        if (!decrypted.isEmpty()) {
            environment.getPropertySources().addFirst(
                    new MapPropertySource("decryptedConfig", decrypted));
            log.info("已解密 {} 个配置项", decrypted.size());
        }
    }

    private String resolveEncryptionKey(Environment environment) {
        String fromEnv = environment.getProperty(ENV_KEY);
        if (fromEnv != null && !fromEnv.isEmpty()) {
            log.info("从环境变量 {} 加载加密密钥", ENV_KEY);
            return fromEnv;
        }
        String fromConfig = environment.getProperty(CONFIG_KEY);
        if (fromConfig != null && !fromConfig.isEmpty()) {
            log.info("从配置项 {} 加载加密密钥", CONFIG_KEY);
            return fromConfig;
        }
        return null;
    }
}
