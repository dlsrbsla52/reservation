package com.media.bus.common.configuration;

import com.media.bus.common.logging.MdcContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/// Spring Boot 4 호환 비동기 실행기 설정.
///
/// Spring Boot 4에서 `TaskExecutorConfigurations$AsyncConfigurerWrapperConfiguration`이
/// 기존 `AsyncConfigurer`를 래핑하여 두 번째 `AsyncConfigurer` 빈을 생성한다.
/// Spring Framework 7은 `AsyncConfigurer` 빈이 2개 이상이면 `IllegalStateException`을 던지므로,
/// `implements AsyncConfigurer` 대신 `TaskDecorator` 빈 등록 방식으로 전환한다.
/// Spring Boot 4는 등록된 `TaskDecorator` 빈을 자동으로 기본 async 실행기에 적용한다.
@Slf4j
@Configuration
@EnableAsync
@Profile({"default", "local", "dev", "prod"})
public class ThreadPoolConfig {

    /// `@Async` 실행기에 MDC 컨텍스트(requestId, traceId, memberId 등)를 전파하는 TaskDecorator.
    ///
    /// Spring Boot 4가 이 빈을 자동으로 기본 async 실행기(`applicationTaskExecutor`)에 적용한다.
    /// 태스크 제출 시점에 호출 스레드의 MDC 스냅샷을 캡처하고,
    /// 실행 스레드에서 복원한 뒤 작업 완료 후 정리한다.
    @Bean
    public TaskDecorator mdcTaskDecorator() {
        return MdcContextUtil::wrap;
    }

    /// CPU-bound 작업을 위한 전용 스레드 풀 (ForkJoinPool 기반).
    ///
    /// work-stealing 알고리즘을 사용하여 CPU 코어를 최대한 활용하도록 설계되었습니다.
    /// 스레드 수는 시스템의 가용 프로세서 코어 수에 맞춰 자동으로 설정됩니다.
    /// 계산 집약적인 작업(예: 복잡한 계산, 데이터 처리)에 사용하는 것을 권장합니다.
    /// ForkJoinPool은 `TaskDecorator`를 직접 지원하지 않으므로 수동 wrap 패턴으로 MDC 전파를 보장한다.
    ///
    /// @return CPU-bound 작업에 최적화된 Executor
    @SuppressWarnings("resource") // ExecutorService(AutoCloseable)의 생명주기는 Spring 컨텍스트가 관리
    @Bean("getForkJoinPoolExecutor")
    public Executor getForkJoinPoolExecutor(TaskDecorator mdcTaskDecorator) {
        Executor forkJoinPool = Executors.newWorkStealingPool();
        return runnable -> forkJoinPool.execute(mdcTaskDecorator.decorate(runnable));
    }

    /// IO-bound 작업을 위한 Virtual Thread 기반 실행기.
    ///
    /// Virtual Thread는 태스크마다 경량 스레드를 생성하여 블로킹 IO 처리에 최적화되어 있다.
    /// `@Async("IoBoundExecutor")`로 명시적으로 지정하여 사용한다.
    /// `TaskDecorator`를 수동 wrap 패턴으로 적용하여 MDC 전파를 보장한다.
    ///
    /// @return Virtual Thread 기반 IO-bound 실행기
    @SuppressWarnings("resource") // ExecutorService(AutoCloseable)의 생명주기는 Spring 컨텍스트가 관리
    @Bean("IoBoundExecutor")
    public Executor getCpuBoundExecutor(TaskDecorator mdcTaskDecorator) {
        Executor vtExecutor = Executors.newVirtualThreadPerTaskExecutor();
        return runnable -> vtExecutor.execute(mdcTaskDecorator.decorate(runnable));
    }
}