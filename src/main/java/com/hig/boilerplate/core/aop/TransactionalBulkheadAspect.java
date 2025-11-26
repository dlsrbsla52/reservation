package com.hig.boilerplate.core.aop;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class TransactionalBulkheadAspect {

    private final BulkheadRegistry bulkheadRegistry;
    private static final String BULKHEAD_NAME = "orderDatabase";

    private final ThreadLocal<Boolean> hasPermit = ThreadLocal.withInitial(() -> false);

    /**
     * DB 커넥션 접근 제어 포인트컷.
     * 1. Repository 인터페이스 (JpaRepository 등)
     * 2. @Repository 클래스 (Custom DAO 등)
     * 3. @Transactional 메서드 (Service 메서드 등)
     * 4. @Transactional 클래스 (클래스 레벨 트랜잭션 설정)
     */
    @Pointcut("target(org.springframework.data.repository.Repository) || "
        + "@within(org.springframework.stereotype.Repository) || "
        + "@annotation(org.springframework.transaction.annotation.Transactional) || "
        + "@within(org.springframework.transaction.annotation.Transactional)")
    public void databaseAccessLayer() {

    }

    @Around("databaseAccessLayer()")
    public Object applyBulkhead(ProceedingJoinPoint joinPoint) throws Throwable {
        // 재진입 방지
        if (Boolean.TRUE.equals(hasPermit.get())) {
            return joinPoint.proceed();
        }

        Bulkhead bulkhead = bulkheadRegistry.bulkhead(BULKHEAD_NAME);

        // 트랜잭션 시작 전(HIGHEST_PRECEDENCE) 퍼밋 획득
        // 실패 시 즉시 예외 발생 -> DB 커넥션 요청조차 하지 않음
        bulkhead.acquirePermission();
        hasPermit.set(true);

        if (log.isDebugEnabled()) {
            log.debug("Bulkhead permit acquired. Calls: {}", bulkhead.getMetrics().getAvailableConcurrentCalls());
        }

        try {
            return joinPoint.proceed();
        } finally {
            // 트랜잭션 종료(Commit/Rollback) 후 퍼밋 반납
            bulkhead.releasePermission();
            hasPermit.remove();

            if (log.isDebugEnabled()) {
                log.debug("Bulkhead permit released.");
            }
        }
    }
}