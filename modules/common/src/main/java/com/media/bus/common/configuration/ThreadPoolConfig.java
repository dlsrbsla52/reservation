package com.media.bus.common.configuration;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
@EnableAsync
@Profile({"default", "local", "dev", "prod"})
public class ThreadPoolConfig {

    /**
     * @Async 실행기에 MDC 컨텍스트(requestId, traceId, userId 등)를 전파하는 TaskDecorator.
     *
     * <p>태스크 제출 시점에 호출 스레드의 MDC 스냅샷을 캡처하고,
     * 실행 스레드에서 복원한 뒤 작업 완료 후 정리한다.
     * 이 빈이 등록되어 있으면 {@code getForkJoinPoolExecutor}, {@code IoBoundExecutor} 양쪽 모두
     * {@code @Async} 메서드에서 {@code log.info()} 만으로 MDC 필드가 자동으로 출력된다.
     */
    @Bean
    public TaskDecorator mdcTaskDecorator() {
        return runnable -> {
            // 태스크 제출 시점(호출 스레드)에서 MDC 스냅샷 캡처
            Map<String, String> mdc = MDC.getCopyOfContextMap();
            return () -> {
                // 실행 스레드에서 복원
                if (mdc != null) {
                    MDC.setContextMap(mdc);
                }
                try {
                    runnable.run();
                } finally {
                    // 풀 스레드 재사용 시 오염 방지
                    MDC.clear();
                }
            };
        };
    }

    /**
     * CPU-bound 작업을 위한 전용 스레드 풀 (ForkJoinPool 기반).
     * <p>
     * work-stealing 알고리즘을 사용하여 CPU 코어를 최대한 활용하도록 설계되었습니다.
     * 스레드 수는 시스템의 가용 프로세서 코어 수에 맞춰 자동으로 설정됩니다. (Runtime.getRuntime().availableProcessors())
     * 계산 집약적인 작업(예: 복잡한 계산, 데이터 처리)에 사용하는 것을 권장합니다.
     * </p>
     *
     * <p>ForkJoinPool 은 {@code TaskDecorator}를 직접 지원하지 않으므로
     * {@code mdcTaskDecorator}로 Runnable을 래핑하여 MDC 전파를 보장한다.
     *
     * @return CPU-bound 작업에 최적화된 Executor
     */
    @Bean("getForkJoinPoolExecutor")
    public Executor getForkJoinPoolExecutor(TaskDecorator mdcTaskDecorator) {
        Executor forkJoinPool = Executors.newWorkStealingPool();
        return runnable -> forkJoinPool.execute(mdcTaskDecorator.decorate(runnable));
    }

    /**
     * IO-bound 작업을 위한 전용 스레드 풀 (ThreadPoolTaskExecutor 기반).
     *
     * @return IO-bound 작업에 최적화된 Executor
     */
    @Bean("IoBoundExecutor")
    public Executor getCpuBoundExecutor(TaskDecorator mdcTaskDecorator) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int coreCount = Runtime.getRuntime().availableProcessors() * 2;
        executor.setCorePoolSize(coreCount);
        executor.setMaxPoolSize(coreCount);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("IoBound-");
        executor.setTaskDecorator(mdcTaskDecorator);
        executor.initialize();
        return executor;
    }
}
