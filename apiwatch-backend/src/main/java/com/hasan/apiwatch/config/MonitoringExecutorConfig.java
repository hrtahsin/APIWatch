package com.hasan.apiwatch.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class MonitoringExecutorConfig {

    @Bean
    ThreadPoolTaskExecutor monitoringTaskExecutor(
            @Value("${apiwatch.scheduler.worker-pool-size:4}") int workerPoolSize,
            @Value("${apiwatch.scheduler.queue-capacity:100}") int queueCapacity
    ) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int normalizedWorkerPoolSize = Math.max(workerPoolSize, 1);
        executor.setCorePoolSize(normalizedWorkerPoolSize);
        executor.setMaxPoolSize(normalizedWorkerPoolSize);
        executor.setQueueCapacity(Math.max(queueCapacity, 0));
        executor.setThreadNamePrefix("apiwatch-monitor-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
