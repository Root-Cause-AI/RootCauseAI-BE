package com.example.incidentinvestigator.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Async configuration for investigation job execution.
 *
 * <p>Provides a dedicated thread pool for AI investigation tasks,
 * separating them from the main HTTP request threads. This allows
 * investigations to run in the background while clients poll for status.</p>
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "investigationExecutor")
    public Executor investigationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("investigation-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
