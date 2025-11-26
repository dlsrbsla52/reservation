package com.hig.boilerplate.core.aop;

import com.hig.boilerplate.core.annotation.BoundedConcurrency;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;

/**
 * {@link BoundedConcurrency} 어노테이션을 처리하는 AOP Aspect 입니다.
 * <p>
 * 이 Aspect는 {@code @BoundedConcurrency}가 붙은 메서드 실행을 가로채,
 * 메서드 실행 전에 지정된 {@link Semaphore}의 허가를 획득하고,
 * 메서드가 반환한 {@link CompletableFuture}가 완료될 때 허가를 반납하는 로직을 수행합니다.
 * </p>
 *
 * <h3>사용법</h3>
 * <p>
 * 개발자는 이 클래스를 직접 호출할 필요가 없습니다.
 * 대신, 비동기 제어가 필요한 서비스 메서드에 {@link BoundedConcurrency} 어노테이션을 적용하여 기능을 활성화합니다.
 * 자세한 사용법과 전체 코드 예제는 {@link BoundedConcurrency} 어노테이션의 Javadoc을 참고하십시오.
 * </p>
 *
 * @see BoundedConcurrency
 */
@Aspect
@Component
@SuppressWarnings("unused")
public class BoundedConcurrencyAspect {

    private static final Logger log = LoggerFactory.getLogger(BoundedConcurrencyAspect.class);
    private final ApplicationContext context;

    public BoundedConcurrencyAspect(ApplicationContext context) {
        this.context = context;
    }

    @Around("@annotation(com.hig.boilerplate.core.annotation.BoundedConcurrency)")
    public Object controlConcurrency(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        BoundedConcurrency annotation = method.getAnnotation(BoundedConcurrency.class);

        // 어노테이션에 지정된 이름으로 Semaphore Bean을 찾는다.
        Semaphore semaphore = context.getBean(annotation.value(), Semaphore.class);

        try {
            // Semaphore 허가 요청
            semaphore.acquire();
            log.trace("Semaphore acquired for [{}]. available permits: {}", method.getName(), semaphore.availablePermits());
        } catch (InterruptedException e) {
            log.warn("Semaphore acquire interrupted for method [{}].", method.getName(), e);
            Thread.currentThread().interrupt();
            // 메서드가 CompletableFuture를 반환할 경우를 대비해 실패한 Future를 반환
            if (CompletableFuture.class.isAssignableFrom(method.getReturnType())) {
                return CompletableFuture.failedFuture(e);
            }
            throw e;
        } catch (Exception e) {
            log.warn("Semaphore acquire failed for method [{}].", method.getName(), e);
            if (CompletableFuture.class.isAssignableFrom(method.getReturnType())) {
                return CompletableFuture.failedFuture(e);
            }
            throw e;
        }

        // 원래의 메서드를 실행하고, 완료되면(whenComplete) Semaphore를 반납
        try {
            // joinPoint.proceed()는 @Async가 적용된 프록시 메서드를 실행
            // 반환 값은 CompletableFuture
            return ((CompletableFuture<?>) joinPoint.proceed())
                .whenComplete((result, throwable) -> {
                    semaphore.release();
                    log.trace("Semaphore released for [{}]. available permits: {}", method.getName(), semaphore.availablePermits());
                });
        } catch (Throwable e) {
            // joinPoint.proceed() 자체에서 예외가 발생한 경우 (e.g. CompletableFuture가 아닌 타입을 반환)
            semaphore.release();
            log.trace("Semaphore released for [{}] due to an exception during proceed.", method.getName(), e);
            throw e;
        }
    }
}