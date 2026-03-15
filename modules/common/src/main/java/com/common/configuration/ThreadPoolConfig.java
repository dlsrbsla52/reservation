package com.common.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
@EnableAsync
@Profile({"default", "local", "dev", "prod"})
public class ThreadPoolConfig {

    /**
     * CPU-bound 작업을 위한 전용 스레드 풀 (ForkJoinPool 기반).
     * <p>
     * work-stealing 알고리즘을 사용하여 CPU 코어를 최대한 활용하도록 설계되었습니다.
     * 스레드 수는 시스템의 가용 프로세서 코어 수에 맞춰 자동으로 설정됩니다. (Runtime.getRuntime().availableProcessors())
     * 계산 집약적인 작업(예: 복잡한 계산, 데이터 처리)에 사용하는 것을 권장합니다.
     * </p>
     * @return CPU-bound 작업에 최적화된 Executor
     */
    @Bean("getForkJoinPoolExecutor")
    public Executor getForkJoinPoolExecutor() {
        return Executors.newWorkStealingPool();
    }

    /**
     * IO-bound 작업을 위한 전용 스레드 풀 (ThreadPoolTaskExecutor 기반).
     *
     * @return IO-bound 작업에 최적화된 Executor
     */
    @Bean("IoBoundExecutor")
    public Executor getCpuBoundExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int coreCount = Runtime.getRuntime().availableProcessors() * 2;
        executor.setCorePoolSize(coreCount);
        executor.setMaxPoolSize(coreCount);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("IoBound-");
        executor.initialize();
        return executor;
    }
}
