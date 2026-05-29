package com.mars.linker.broker.config;

import com.mars.linker.broker.config.encrypt.ConfigDecryptor;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * HikariCP 数据库连接池配置，仅在 storage-mode=db 时激活。
 * <p>
 * 设计决策：
 * - 所有参数从 MarsLinkerMqttBrokerProperties 读取
 * - 密码字段通过 ConfigDecryptor 解密后传入，支持 ENC() 格式
 * - 连接泄漏检测阈值可配置，生产环境建议 60 秒
 * </p>
 */
@Configuration
@ConditionalOnProperty(prefix = "mars.linker.broker", name = "storage-mode", havingValue = "db")
@EnableConfigurationProperties(MarsLinkerMqttBrokerProperties.class)
public class DbPoolConfiguration {

    private static final Logger log = LoggerFactory.getLogger(DbPoolConfiguration.class);

    @Bean
    public DataSource brokerDataSource(MarsLinkerMqttBrokerProperties props) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(props.getStorageDbJdbcUrl());
        config.setUsername(props.getStorageDbUsername());

        String password = props.getStorageDbPassword();
        String encryptionKey = resolveEncryptionKey();
        if (encryptionKey != null && password != null && password.startsWith("ENC(")) {
            ConfigDecryptor decryptor = new ConfigDecryptor(encryptionKey);
            password = decryptor.decryptIfEncrypted(password);
        }
        config.setPassword(password);

        config.setMinimumIdle(props.getDbPoolMinIdle());
        config.setMaximumPoolSize(props.getDbPoolMaxSize());
        config.setConnectionTimeout(props.getDbPoolConnectionTimeoutMs());
        config.setIdleTimeout(props.getDbPoolIdleTimeoutMs());
        config.setMaxLifetime(props.getDbPoolMaxLifetimeMs());
        config.setLeakDetectionThreshold(props.getDbPoolLeakDetectionMs());
        config.setPoolName("broker-hikari");

        log.info("HikariCP 连接池配置: maxSize={}, minIdle={}, connectionTimeout={}ms, leakDetection={}ms",
                props.getDbPoolMaxSize(), props.getDbPoolMinIdle(),
                props.getDbPoolConnectionTimeoutMs(), props.getDbPoolLeakDetectionMs());

        return new HikariDataSource(config);
    }

    private String resolveEncryptionKey() {
        String fromEnv = System.getenv("MARS_CONFIG_ENCRYPTION_KEY");
        if (fromEnv != null && !fromEnv.isEmpty()) {
            return fromEnv;
        }
        return System.getProperty("mars.linker.broker.config-encryption-key");
    }
}
