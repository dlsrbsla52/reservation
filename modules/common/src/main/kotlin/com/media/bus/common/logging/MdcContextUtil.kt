package com.media.bus.common.logging

import org.slf4j.MDC
import java.util.concurrent.Callable

/**
 * ## MDC(Mapped Diagnostic Context) 컨텍스트를 비동기 작업에 전파하는 유틸리티
 *
 * Virtual Thread 환경에서 CompletableFuture, @Async 등 비동기 실행 시
 * MDC 컨텍스트가 자동으로 복사되지 않는 문제를 해결한다.
 *
 * 사용 예:
 * ```
 * CompletableFuture.runAsync(MdcContextUtil.wrap {
 *     // MDC 컨텍스트가 자동으로 복사됨
 *     log.info("비동기 작업 실행")
 * })
 * ```
 */
object MdcContextUtil {

    /**
     * 현재 스레드의 MDC 컨텍스트를 캡처한다.
     *
     * @return 현재 MDC 컨텍스트의 복사본 (null인 경우 빈 Map 반환)
     */
    @JvmStatic
    fun capture(): Map<String, String> =
        MDC.getCopyOfContextMap() ?: HashMap()

    /**
     * Runnable을 MDC 컨텍스트 전파 래퍼로 감싼다.
     * 호출 시점의 MDC를 캡처하여 실행 시점에 복원한다.
     *
     * @param runnable 감쌀 Runnable
     * @return MDC 전파가 적용된 Runnable
     */
    @JvmStatic
    fun wrap(runnable: Runnable): Runnable {
        val capturedContext = capture()
        return Runnable {
            val previousContext = MDC.getCopyOfContextMap()
            try {
                MDC.setContextMap(capturedContext)
                runnable.run()
            } finally {
                // 이전 컨텍스트 복원 (스레드 풀 재사용 시 오염 방지)
                if (previousContext != null) {
                    MDC.setContextMap(previousContext)
                } else {
                    MDC.clear()
                }
            }
        }
    }

    /**
     * Callable을 MDC 컨텍스트 전파 래퍼로 감싼다.
     * 호출 시점의 MDC를 캡처하여 실행 시점에 복원한다.
     *
     * @param callable 감쌀 Callable
     * @return MDC 전파가 적용된 Callable
     */
    @JvmStatic
    fun <T> wrap(callable: Callable<T>): Callable<T> {
        val capturedContext = capture()
        return Callable {
            val previousContext = MDC.getCopyOfContextMap()
            try {
                MDC.setContextMap(capturedContext)
                callable.call()
            } finally {
                // 이전 컨텍스트 복원 (스레드 풀 재사용 시 오염 방지)
                if (previousContext != null) {
                    MDC.setContextMap(previousContext)
                } else {
                    MDC.clear()
                }
            }
        }
    }
}
