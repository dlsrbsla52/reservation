package com.media.bus.common.core.aop

import com.media.bus.common.configuration.BulkheadProperties
import io.github.resilience4j.bulkhead.Bulkhead
import io.github.resilience4j.bulkhead.BulkheadRegistry
import jakarta.annotation.PostConstruct
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Pointcut
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order

/**
 * ## DB 동시 접근 수를 Resilience4j 세마포어 Bulkhead로 제한하는 AOP Aspect
 *
 * **실행 순서 -- @Order(HIGHEST_PRECEDENCE)**
 *
 * 반드시 Spring의 `TransactionInterceptor`보다 먼저(바깥쪽에서) 실행되어야 한다.
 * `TransactionInterceptor`는 실행 시점에 HikariCP에서 물리적 DB 커넥션을 획득하므로,
 * Bulkhead가 트랜잭션 안쪽에서 동작한다면 커넥션을 이미 점유한 채 스레드가 대기하게 되어
 * Bulkhead의 목적(커넥션 풀 고갈 방지)이 상실된다.
 *
 * **재진입 방지 -- ScopedValue**
 *
 * 동일 실행 흐름 내 중첩 `@Transactional` 호출 시 추가 퍼밋 소비를 막기 위해
 * [ScopedValue]를 사용한다. Virtual Thread 환경에서 스코프를 벗어나면 자동 해제되므로
 * `ThreadLocal`과 달리 별도 remove() 없이도 안전하다.
 */
@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE)
class TransactionalBulkheadAspect(
    private val bulkheadRegistry: BulkheadRegistry,
    private val bulkheadProperties: BulkheadProperties,
) {

    private val log = LoggerFactory.getLogger(TransactionalBulkheadAspect::class.java)

    /** Bulkhead 인스턴스 캐시. databaseName은 애플리케이션 수명 동안 불변이므로 초기화 시점에 한 번만 조회한다. */
    private lateinit var bulkhead: Bulkhead

    @PostConstruct
    fun init() {
        this.bulkhead = bulkheadRegistry.bulkhead(bulkheadProperties.databaseName)
    }

    companion object {
        // ScopedValue는 불변이며 특정 스코프 내에서만 값이 유효하다.
        // Virtual Thread 환경에서 ThreadLocal을 대체하는 컨텍스트 전달 수단.
        private val HAS_PERMIT: ScopedValue<Boolean> = ScopedValue.newInstance()
    }

    @Pointcut(
        "target(org.springframework.data.repository.Repository) || " +
            "@within(org.springframework.stereotype.Repository) || " +
            "@annotation(org.springframework.transaction.annotation.Transactional) || " +
            "@within(org.springframework.transaction.annotation.Transactional)"
    )
    fun databaseAccessLayer() {
    }

    @Around("databaseAccessLayer()")
    fun applyBulkhead(joinPoint: ProceedingJoinPoint): Any? {
        // isBound(): 현재 실행 흐름이 이미 퍼밋을 보유하고 있으면 추가 획득 없이 진행 (재진입 허용)
        if (HAS_PERMIT.isBound) {
            return joinPoint.proceed()
        }

        // acquirePermission()은 내부적으로 maxWaitDuration까지만 대기(tryAcquire)한다.
        // 시간 초과 시 즉시 BulkheadFullException을 던지므로 무한 대기가 아님.
        bulkhead.acquirePermission()

        if (log.isDebugEnabled) {
            log.debug("Bulkhead 퍼밋 획득. 남은 동시 호출 수: {}", bulkhead.metrics.availableConcurrentCalls)
        }

        try {
            // ScopedValue.where().call()의 Callable은 throws Exception만 허용하므로
            // joinPoint.proceed()의 Throwable을 전달하려면 래핑이 필요하다.
            return ScopedValue.where(HAS_PERMIT, true)
                .call<Any?, Exception> {
                    try {
                        joinPoint.proceed()
                    } catch (e: Throwable) {
                        throw BulkheadCallException(e)
                    }
                }
        } catch (e: BulkheadCallException) {
            // BulkheadCallException은 이 클래스 내부에서만 생성되는 마커 예외이므로
            // cause가 항상 원본 Throwable임이 보장된다.
            throw e.cause!!
        } finally {
            bulkhead.releasePermission()

            if (log.isDebugEnabled) {
                log.debug("Bulkhead 퍼밋 반납.")
            }
        }
    }

    /**
     * ScopedValue 내부에서 발생한 Throwable을 외부로 전달하기 위한 전용 마커 예외.
     *
     * Callable 시그니처(throws Exception)의 제약을 우회하면서,
     * catch 블록에서 이 클래스만 선택적으로 잡아 안전하게 원본 예외를 언래핑한다.
     */
    private class BulkheadCallException(cause: Throwable) : RuntimeException(cause)
}
