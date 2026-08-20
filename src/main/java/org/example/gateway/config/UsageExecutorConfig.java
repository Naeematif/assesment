package org.example.gateway.config;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UsageExecutorConfig {

    private static final Logger log = LoggerFactory.getLogger(UsageExecutorConfig.class);

    /**
     * Executor backing usage persistence.
     *
     * <p>Bounded queue plus {@link ThreadPoolExecutor.CallerRunsPolicy}: if metering falls behind,
     * back-pressure lands on the request thread rather than events being discarded. A dropped usage
     * event is revenue that never gets invoiced, which is a worse outcome than a slower request.
     *
     * <p>Setting {@code gateway.usage.async=false} makes writes synchronous, which tests use to
     * assert on persisted usage without sleeping.
     */
    @Bean(name = "usageExecutor", destroyMethod = "")
    public Executor usageExecutor(GatewayProperties properties) {
        if (!properties.getUsage().isAsync()) {
            log.info("Usage tracking configured to run synchronously on the request thread");
            return Runnable::run;
        }
        int threads = properties.getUsage().getWorkerThreads();
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                threads, threads,
                60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(properties.getUsage().getQueueCapacity()),
                runnable -> {
                    Thread thread = new Thread(runnable, "usage-writer");
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.CallerRunsPolicy());
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }
}
