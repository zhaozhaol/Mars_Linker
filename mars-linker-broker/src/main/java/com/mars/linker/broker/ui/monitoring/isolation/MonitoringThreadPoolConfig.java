package com.mars.linker.broker.ui.monitoring.isolation;

import com.mars.linker.broker.netty.MqttProtocolHandler;
import com.mars.linker.broker.ui.config.MarsLinkerUiProperties;
import com.mars.linker.broker.ui.monitoring.SubscriptionDetailService;
import com.mars.linker.broker.ui.monitoring.sampling.SampleRateFilter;
import com.mars.linker.broker.ui.monitoring.sampling.TimeWindowAggregator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableAsync
@ConditionalOnProperty(prefix = "mars.linker.ui", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MonitoringThreadPoolConfig {

    @Bean("monitoring-executor")
    public ExecutorService monitoringExecutor(MarsLinkerUiProperties props) {
        int coreSize = props.getMonitoringThreadPoolSize();
        return new ThreadPoolExecutor(
                coreSize, coreSize,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(1024),
                r -> {
                    Thread t = new Thread(r, "monitoring-" + System.currentTimeMillis());
                    t.setDaemon(true);
                    return t;
                },
                (r, executor) -> {
                    MonitoringFaultBoundary.incrementDiscardCount();
                }
        );
    }

    @Bean("monitoring-push-executor")
    public ExecutorService pushExecutor(MarsLinkerUiProperties props) {
        int coreSize = props.getPushThreadPoolSize();
        return new ThreadPoolExecutor(
                coreSize, coreSize,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(512),
                r -> {
                    Thread t = new Thread(r, "monitoring-push-" + System.currentTimeMillis());
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.DiscardOldestPolicy()
        );
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
