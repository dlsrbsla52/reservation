package com.media.bus.common.core.aop

import com.media.bus.common.core.annotation.BoundedConcurrency
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Semaphore

/**
 * ## [BoundedConcurrency] 어노테이션을 처리하는 AOP Aspect
 *
 * 이 Aspect는 `@BoundedConcurrency`가 붙은 메서드 실행을 가로채,
 * 메서드 실행 전에 지정된 [Semaphore]의 허가를 획득하고,
 * 메서드가 반환한 [CompletableFuture]가 완료될 때 허가를 반납하는 로직을 수행한다.
 */
@Aspect
@Component
@Suppress("unused")
class BoundedConcurrencyAspect(private val context: ApplicationContext) {

    private val log = LoggerFactory.getLogger(BoundedConcurrencyAspect::class.java)

    @Around("@annotation(com.media.bus.common.core.annotation.BoundedConcurrency)")
    fun controlConcurrency(joinPoint: ProceedingJoinPoint): Any? {
        val signature = joinPoint.signature as MethodSignature
        val method = signature.method
        val annotation = method.getAnnotation(BoundedConcurrency::class.java)

        // 어노테이션에 지정된 이름으로 Semaphore Bean을 찾는다.
        val semaphore = context.getBean(annotation.value, Semaphore::class.java)

        try {
            // Semaphore 허가 요청
            semaphore.acquire()
            log.trace("Semaphore acquired for [{}]. available permits: {}", method.name, semaphore.availablePermits())
        } catch (e: InterruptedException) {
            log.warn("Semaphore acquire interrupted for method [{}].", method.name, e)
            Thread.currentThread().interrupt()
            // 메서드가 CompletableFuture를 반환할 경우를 대비해 실패한 Future를 반환
            if (CompletableFuture::class.java.isAssignableFrom(method.returnType)) {
                return CompletableFuture.failedFuture<Any>(e)
            }
            throw e
        } catch (e: Exception) {
            log.warn("Semaphore acquire failed for method [{}].", method.name, e)
            if (CompletableFuture::class.java.isAssignableFrom(method.returnType)) {
                return CompletableFuture.failedFuture<Any>(e)
            }
            throw e
        }

        // 원래의 메서드를 실행하고, 완료되면(whenComplete) Semaphore를 반납
        return try {
            @Suppress("UNCHECKED_CAST")
            (joinPoint.proceed() as CompletableFuture<*>)
                .whenComplete { _, _ ->
                    semaphore.release()
                    log.trace("Semaphore released for [{}]. available permits: {}", method.name, semaphore.availablePermits())
                }
        } catch (e: Throwable) {
            // joinPoint.proceed() 자체에서 예외가 발생한 경우
            semaphore.release()
            log.trace("Semaphore released for [{}] due to an exception during proceed.", method.name, e)
            throw e
        }
    }
}
