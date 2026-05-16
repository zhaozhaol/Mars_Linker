package com.mars.linker.broker.config;

import com.mars.linker.broker.netty.MqttProtocolHandler;
import com.mars.linker.broker.netty.NettyMqttBrokerServer;
import com.mars.linker.broker.ui.rejection.RejectionMessageCollector;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MqttHandlerConfig {

    @Bean
    @ConditionalOnBean(NettyMqttBrokerServer.class)
    public MqttProtocolHandler mqttProtocolHandler(NettyMqttBrokerServer server,
                                                   RejectionMessageCollector rejectionMessageCollector) {
        MqttProtocolHandler handler = server.getMqttProtocolHandler();
        handler.setRejectionMessageCollector(rejectionMessageCollector);
        return handler;
    }
}
