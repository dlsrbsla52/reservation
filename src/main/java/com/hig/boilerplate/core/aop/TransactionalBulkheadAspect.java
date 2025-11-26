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

    // ThreadLocal 대신 ScopedValue 선언
    // ScopedValue는 불변이며, 특정 스코프 내에서만 값이 유효함
    private static final ScopedValue<Boolean> HAS_PERMIT = ScopedValue.newInstance();

    @Pointcut("target(org.springframework.data.repository.Repository) || " +
        "@within(org.springframework.stereotype.Repository) || " +
        "@annotation(org.springframework.transaction.annotation.Transactional) || " +
        "@within(org.springframework.transaction.annotation.Transactional)")
    public void databaseAccessLayer() {}

    @Around("databaseAccessLayer()")
    public Object applyBulkhead(ProceedingJoinPoint joinPoint) throws Throwable {
        // isBound()를 통해 현재 스코프에 값이 바인딩되어 있는지 확인 (재진입 방지)
        if (HAS_PERMIT.isBound()) {
            return joinPoint.proceed();
        }

        Bulkhead bulkhead = bulkheadRegistry.bulkhead(BULKHEAD_NAME);
        bulkhead.acquirePermission();

        if (log.isDebugEnabled()) {
            log.debug("Bulkhead permit acquired. Calls: {}", bulkhead.getMetrics().getAvailableConcurrentCalls());
        }

        try {
            // 이 블록 내부(call)에서만 HAS_PERMIT이 true 블록을 벗어나면 자동 소멸됨.
            // 별도의 remove() 호출이 필요 없어 안전합
            return ScopedValue.where(HAS_PERMIT, true)
                .call(() -> {
                    try {
                        return joinPoint.proceed();
                    } catch (Throwable e) {
                        // call()은 Exception을 던지므로 Throwable을 처리하기 위한 래핑 필요
                        throw new RuntimeException(e);
                    }
                });
        } catch (RuntimeException e) {
            // 래핑된 원본 예외(Throwable)를 다시 꺼내서 던짐
            if (e.getCause() != null) {
                throw e.getCause();
            }
            throw e;
        } finally {
            // 트랜잭션 종료 후 퍼밋 반납
            bulkhead.releasePermission();

            if (log.isDebugEnabled()) {
                log.debug("Bulkhead permit released.");
            }
        }
    }
}