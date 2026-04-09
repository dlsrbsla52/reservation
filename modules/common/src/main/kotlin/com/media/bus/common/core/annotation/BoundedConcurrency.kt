package com.media.bus.common.core.annotation

/**
 * ## Semaphore를 사용하여 비동기 작업의 동시 실행 수를 제한하는 어노테이션
 *
 * 이 어노테이션은 비동기 처리를 위한 `@Async` 어노테이션과 함께 사용하도록 설계되었다.
 * `@Async`가 작업을 백그라운드 스레드에서 실행하도록 위임하면,
 * `@BoundedConcurrency`는 그 작업의 진입로를 제어하는 '문지기' 역할을 한다.
 *
 * 메서드가 반환하는 `CompletableFuture`의 완료 시점을 기준으로 Semaphore 허가를
 * 자동으로 획득하고 반납하며, 스레드 풀과 큐가 가득 찼을 때 추가 요청은
 * Semaphore 허가를 받을 때까지 안전하게 대기한다.
 *
 * @property value 제어에 사용할 Semaphore Spring Bean의 이름
 */
@MustBeDocumented
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class BoundedConcurrency(
    /** 제어에 사용할 Semaphore Spring Bean의 이름을 지정한다. */
    val value: String,
)
