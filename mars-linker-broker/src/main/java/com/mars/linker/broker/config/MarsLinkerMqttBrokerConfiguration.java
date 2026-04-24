package com.mars.linker.broker.config;

import com.mars.linker.broker.netty.NettyMqttBrokerServer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Broker 配置装配入口。
 * <p>
 * 目前仅启用 {@link MarsLinkerMqttBrokerProperties} 的配置绑定；
 * 具体监听器由 {@link NettyMqttBrokerServer}
 * 通过 {@code @ConditionalOnProperty} 控制是否启动。
 * </p>
 */
@Configuration
@EnableConfigurationProperties(MarsLinkerMqttBrokerProperties.class)
public class MarsLinkerMqttBrokerConfiguration {
}
