package com.project2.ism.WebConfig;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * A small pool of its own for the routing trial run. Under load it drops work instead of waiting, and it never
 * runs on the payment's own thread, so a payment can never be slowed or failed by the trial run.
 */
@Configuration
public class RoutingTrialConfig {

    @Bean("routingTrialExecutor")
    public Executor routingTrialExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("routing-trial-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        executor.initialize();
        return executor;
    }
}
