package com.media.bus.common.core.aop

import com.media.bus.common.logging.MdcContextUtil
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.slf4j.MDC

/**
 * ## `@Async` 메서드 호출 시 호출 스레드의 MDC 컨텍스트를 비동기 실행 스레드로 자동 전파하는 AOP Aspect
 *
 * ### 동작 방식
 *
 * 이 Aspect는 [com.media.bus.common.configuration.ThreadPoolConfig]의
 * `mdcTaskDecorator`와 두 레이어로 협력한다:
 *
 *   1. **Aspect (호출 스레드)**: `proceed()` 직전에 MDC 스냅샷을 캡처/복원하여
 *     Executor 제출 시점에 올바른 MDC가 설정되어 있음을 보장한다.
 *   2. **TaskDecorator (Executor 레이어)**: `executor.execute()` 시점에 호출 스레드의
 *     MDC를 캡처하여 실행 스레드에 주입한다.
 *
 * ### 커버 범위
 *
 *   - HTTP 요청 컨텍스트 내 `@Async` -- requestId, traceId, memberId 전파
 *   - `@Scheduled`, 이벤트 리스너 등 HTTP 컨텍스트 없는 환경 -- 빈 Map으로 안전 처리
 */
@Aspect
class AsyncMdcAspect {

    @Around("@annotation(org.springframework.scheduling.annotation.Async)")
    fun propagateMdc(joinPoint: ProceedingJoinPoint): Any? {
        // 호출 스레드의 MDC 스냅샷 캡처 (HTTP 컨텍스트 없으면 빈 Map)
        val callerMdc = MdcContextUtil.capture()

        // TaskDecorator는 executor.execute() 호출 시점(= proceed() 내부)의 MDC를 캡처하므로
        // 호출 스레드에 MDC가 확실히 설정되어 있음을 보장한다
        MDC.setContextMap(callerMdc)
        return try {
            joinPoint.proceed()
        } finally {
            // proceed() 내부에서 MDC가 변경되었을 가능성을 방지하여 호출 스레드 상태 복원
            MDC.setContextMap(callerMdc)
        }
    }
}
