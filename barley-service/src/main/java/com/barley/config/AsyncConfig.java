package com.barley.config;

import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Thread-pool configuration for @Async methods.
 *
 * <p>Why these numbers matter (interview gold):
 * <ul>
 *   <li><b>corePoolSize</b>  – threads kept alive even when idle. Set to ~CPU cores for CPU-bound,
 *       higher for I/O-bound (network / DB calls).</li>
 *   <li><b>maxPoolSize</b>   – absolute cap. Beyond this, tasks go to the queue.</li>
 *   <li><b>queueCapacity</b> – bounded queue prevents OOM under burst load.
 *       If the queue is full AND threads == max, {@link ThreadPoolExecutor.CallerRunsPolicy}
 *       runs the task on the calling thread, providing natural back-pressure.</li>
 * </ul>
 */
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    /**
     * General-purpose async executor used when @Async has no explicit qualifier.
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(32);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("barley-async-");
        executor.setKeepAliveSeconds(60);
        // CallerRunsPolicy = back-pressure: if pool+queue are full, caller thread executes
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // Wait for in-flight tasks to finish on shutdown (graceful)
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /**
     * Dedicated executor for heavy processing tasks to avoid starving the main pool.
     */
    @Bean(name = "heavyTaskExecutor")
    public Executor heavyTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("barley-heavy-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }

    @Override
    public Executor getAsyncExecutor() {
        return taskExecutor();
    }

    /**
     * Handles uncaught exceptions thrown by @Async methods.
     * In production, send to PagerDuty / Sentry / Datadog.
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) ->
            org.slf4j.LoggerFactory.getLogger(AsyncConfig.class)
                .error("Uncaught async exception in method '{}': {}", method.getName(), ex.getMessage(), ex);
    }
}
