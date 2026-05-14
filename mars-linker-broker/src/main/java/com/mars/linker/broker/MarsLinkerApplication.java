package com.mars.linker.broker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * MarsLinker Broker 应用启动入口。
 * <p>
 * 进程内同时承载两类能力：
 * </p>
 * <ul>
 *   <li>Spring Boot 管理面：Actuator 健康检查、指标暴露。</li>
 *   <li>MQTT 数据面：由 Netty 监听器在配置开启时启动。</li>
 * </ul>
 */
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class, RedisAutoConfiguration.class})
@EnableScheduling
public class MarsLinkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(MarsLinkerApplication.class, args);
    }
}
