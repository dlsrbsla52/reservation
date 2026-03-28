package com.media.bus.common.core.aop;

import com.media.bus.common.configuration.BulkheadProperties;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/// DB 동시 접근 수를 Resilience4j 세마포어 Bulkhead로 제한하는 AOP Aspect.
///
/// **실행 순서 — @Order(HIGHEST\_PRECEDENCE)**
///
/// 반드시 Spring의 `TransactionInterceptor`보다 먼저(바깥쪽에서) 실행되어야 한다.
/// `TransactionInterceptor`는 실행 시점에 HikariCP에서 물리적 DB 커넥션을 획득하므로,
/// Bulkhead가 트랜잭션 안쪽에서 동작한다면 커넥션을 이미 점유한 채 스레드가 대기하게 되어
/// Bulkhead의 목적(커넥션 풀 고갈 방지)이 상실된다.
/// `HIGHEST_PRECEDENCE`는 커넥션 획득 전에 퍼밋을 제어하여 이 문제를 방지한다.
///
/// **재진입 방지 — ScopedValue**
///
/// 동일 실행 흐름 내 중첩 `@Transactional` 호출 시 추가 퍼밋 소비를 막기 위해
/// [ScopedValue]를 사용한다. Virtual Thread 환경에서 스코프를 벗어나면 자동 해제되므로
/// `ThreadLocal`과 달리 별도 remove() 없이도 안전하다.
/// (프로젝트 규약: Virtual Thread 환경에서 ThreadLocal 사용 금지)
///
/// **Pointcut 범위 설계 결정**
///
/// `@Transactional` 서비스 레이어 메서드를 포함한다.
/// "트랜잭션 = DB 작업"이라는 전제 하에 서비스 레이어 트랜잭션도 DB 커넥션을 소비하므로
/// Bulkhead 범위에 포함하는 것이 의도된 설계다.
/// DB 접근이 없는 `@Transactional` 메서드가 생기면 해당 메서드의 어노테이션을 제거하거나
/// `propagation = NOT_SUPPORTED`를 적용하라.
@Slf4j
@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class TransactionalBulkheadAspect {

    private final BulkheadRegistry bulkheadRegistry;
    private final BulkheadProperties bulkheadProperties;

    /// Bulkhead 인스턴스 캐시.
    /// databaseName은 애플리케이션 수명 동안 불변이므로 초기화 시점에 한 번만 조회한다.
    /// `BulkheadRegistry.bulkhead(name)`은 매 호출마다 ConcurrentHashMap lookup을 수행하므로
    /// 캐싱으로 불필요한 반복 조회를 제거한다.
    private Bulkhead bulkhead;

    @PostConstruct
    void init() {
        this.bulkhead = bulkheadRegistry.bulkhead(bulkheadProperties.getDatabaseName());
    }

    // ScopedValue는 불변이며 특정 스코프 내에서만 값이 유효하다.
    // Virtual Thread 환경에서 ThreadLocal을 대체하는 컨텍스트 전달 수단.
    private static final ScopedValue<Boolean> HAS_PERMIT = ScopedValue.newInstance();

    @Pointcut("target(org.springframework.data.repository.Repository) || "
        + "@within(org.springframework.stereotype.Repository) || "
        + "@annotation(org.springframework.transaction.annotation.Transactional) || "
        + "@within(org.springframework.transaction.annotation.Transactional)")
    public void databaseAccessLayer() {
    }

    @Around("databaseAccessLayer()")
    public Object applyBulkhead(ProceedingJoinPoint joinPoint) throws Throwable {
        // isBound(): 현재 실행 흐름이 이미 퍼밋을 보유하고 있으면 추가 획득 없이 진행 (재진입 허용)
        if (HAS_PERMIT.isBound()) {
            return joinPoint.proceed();
        }

        // acquirePermission()은 내부적으로 maxWaitDuration까지만 대기(tryAcquire)한다.
        // 시간 초과 시 즉시 BulkheadFullException을 던지므로 무한 대기가 아님.
        bulkhead.acquirePermission();

        if (log.isDebugEnabled()) {
            log.debug("Bulkhead 퍼밋 획득. 남은 동시 호출 수: {}", bulkhead.getMetrics().getAvailableConcurrentCalls());
        }

        try {
            // ScopedValue.where().call()의 Callable은 throws Exception만 허용하므로
            // joinPoint.proceed()의 Throwable을 전달하려면 래핑이 필요하다.
            // 전용 마커 예외(BulkheadCallException)를 사용하여 비즈니스 로직의 RuntimeException과
            // 명확히 구분한다. (일반 RuntimeException 재사용 시 getCause() 기반 식별이 불가능)
            return ScopedValue.where(HAS_PERMIT, true)
                .call(() -> {
                    try {
                        return joinPoint.proceed();
                    } catch (Throwable e) {
                        throw new BulkheadCallException(e);
                    }
                });
        } catch (BulkheadCallException e) {
            // BulkheadCallException은 이 클래스 내부에서만 생성되는 마커 예외이므로
            // cause가 항상 원본 Throwable임이 보장된다.
            throw e.getCause();
        } finally {
            bulkhead.releasePermission();

            if (log.isDebugEnabled()) {
                log.debug("Bulkhead 퍼밋 반납.");
            }
        }
    }

    /// ScopedValue 내부에서 발생한 Throwable을 외부로 전달하기 위한 전용 마커 예외.
    ///
    /// Callable 시그니처(throws Exception)의 제약을 우회하면서,
    /// catch 블록에서 이 클래스만 선택적으로 잡아 안전하게 원본 예외를 언래핑한다.
    /// 이 클래스 외부에서 인스턴스를 생성하거나 catch해서는 안 된다.
    private static final class BulkheadCallException extends RuntimeException {
        BulkheadCallException(Throwable cause) {
            super(cause);
        }
    }
}