package com.media.bus.common.configuration;

import com.media.bus.common.logging.MdcContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Slf4j
@Configuration
@EnableAsync
@Profile({"default", "local", "dev", "prod"})
public class ThreadPoolConfig implements AsyncConfigurer {

    /**
     * @Async 실행기에 MDC 컨텍스트(requestId, traceId, userId 등)를 전파하는 TaskDecorator.
     *
     * <p>태스크 제출 시점에 호출 스레드의 MDC 스냅샷을 캡처하고,
     * 실행 스레드에서 복원한 뒤 작업 완료 후 정리한다.
     * {@link MdcContextUtil#wrap(Runnable)}에 전파 로직이 위임된다.
     */
    @Bean
    public TaskDecorator mdcTaskDecorator() {
        return MdcContextUtil::wrap;
    }

    /**
     * executor 이름을 명시하지 않은 {@code @Async} 메서드의 기본 실행기.
     *
     * <p>{@code IoBoundExecutor}를 기본으로 지정하여 MDC 자동 전파를 보장한다.
     * executor를 명시하지 않으면 Spring 기본 {@code SimpleAsyncTaskExecutor}가 사용되어
     * {@code mdcTaskDecorator}가 적용되지 않는 문제를 방지한다.
     */
    @Override
    public Executor getAsyncExecutor() {
        return getCpuBoundExecutor(mdcTaskDecorator());
    }

    /**
     * {@code @Async} 메서드에서 발생한 미처리 예외 핸들러.
     * {@code void} 반환 메서드는 예외가 호출부로 전파되지 않으므로 여기서 로깅한다.
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) ->
            log.error("@Async 메서드 [{}#{}] 미처리 예외 발생",
                method.getDeclaringClass().getSimpleName(), method.getName(), ex);
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
    @SuppressWarnings("resource") // ExecutorService(AutoCloseable)의 생명주기는 Spring 컨텍스트가 관리
    @Bean("getForkJoinPoolExecutor")
    public Executor getForkJoinPoolExecutor(TaskDecorator mdcTaskDecorator) {
        Executor forkJoinPool = Executors.newWorkStealingPool();
        return runnable -> forkJoinPool.execute(mdcTaskDecorator.decorate(runnable));
    }

    /**
     * IO-bound 작업을 위한 Virtual Thread 기반 실행기.
     *
     * <p>Virtual Thread는 태스크마다 경량 스레드를 생성하여 블로킹 IO 처리에 최적화되어 있다.
     * {@code ThreadPoolTaskExecutor}는 {@code TaskDecorator}를 지원하지만 VT를 지원하지 않으므로,
     * {@code getForkJoinPoolExecutor}와 동일한 수동 wrap 패턴으로 MDC 전파를 보장한다.
     *
     * @return Virtual Thread 기반 IO-bound 실행기
     */
    @SuppressWarnings("resource") // ExecutorService(AutoCloseable)의 생명주기는 Spring 컨텍스트가 관리
    @Bean("IoBoundExecutor")
    public Executor getCpuBoundExecutor(TaskDecorator mdcTaskDecorator) {
        Executor vtExecutor = Executors.newVirtualThreadPerTaskExecutor();
        return runnable -> vtExecutor.execute(mdcTaskDecorator.decorate(runnable));
    }
}
