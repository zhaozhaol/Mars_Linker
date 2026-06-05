package com.mars.linker.broker.ui.monitoring.isolation;

import com.mars.linker.broker.netty.MqttProtocolHandler;
import com.mars.linker.broker.netty.trace.NamedThreadFactory;
import com.mars.linker.broker.netty.trace.TraceContextTaskDecorator;
import com.mars.linker.broker.ui.config.MarsLinkerUiProperties;
import com.mars.linker.broker.ui.monitoring.SubscriptionDetailService;
import com.mars.linker.broker.ui.monitoring.sampling.SampleRateFilter;
import com.mars.linker.broker.ui.monitoring.sampling.TimeWindowAggregator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
@ConditionalOnProperty(prefix = "mars.linker.ui", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MonitoringThreadPoolConfig {

    @Bean("monitoring-executor")
    public Executor monitoringExecutor(MarsLinkerUiProperties props) {
        int coreSize = props.getMonitoringThreadPoolSize();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("broker-monitor-");
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(coreSize);
        executor.setQueueCapacity(1024);
        executor.setThreadPriority(Thread.NORM_PRIORITY - 1);
        executor.setDaemon(true);
        executor.setTaskDecorator(new TraceContextTaskDecorator());
        executor.setRejectedExecutionHandler((r, ex) -> MonitoringFaultBoundary.incrementDiscardCount());
        executor.initialize();
        return executor;
    }

    @Bean("monitoring-push-executor")
    public Executor pushExecutor(MarsLinkerUiProperties props) {
        int coreSize = props.getPushThreadPoolSize();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("broker-monitor-push-");
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(coreSize);
        executor.setQueueCapacity(512);
        executor.setThreadPriority(Thread.NORM_PRIORITY - 1);
        executor.setDaemon(true);
        executor.setTaskDecorator(new TraceContextTaskDecorator());
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardOldestPolicy());
        executor.initialize();
        return executor;
    }

    @Bean
    public MonitoringResourceBudget monitoringResourceBudget(MarsLinkerUiProperties props) {
        return new MonitoringResourceBudget(props);
    }

    @Bean
    public ApiRateLimiter apiRateLimiter(MarsLinkerUiProperties props) {
        return new ApiRateLimiter(props.getMaxApiQueriesPerSecond());
    }

    @Bean
    public SampleRateFilter sampleRateFilter(MarsLinkerUiProperties props) {
        SampleRateFilter filter = new SampleRateFilter();
        filter.updateSampleRate("connection", props.getConnectionSampleRate());
        filter.updateSampleRate("message", props.getMessageSampleRate());
        filter.updateSampleRate("subscription", props.getSubscriptionSampleRate());
        filter.updateSampleRate("system", props.getSystemSampleRate());
        return filter;
    }

    @Bean
    public TimeWindowAggregator timeWindowAggregator(MarsLinkerUiProperties props) {
        return new TimeWindowAggregator(props.getAggregationWindowMs());
    }

    @Bean
    public SubscriptionDetailService subscriptionDetailService(MqttProtocolHandler protocolHandler) {
        return new SubscriptionDetailService(protocolHandler);
    }
}
