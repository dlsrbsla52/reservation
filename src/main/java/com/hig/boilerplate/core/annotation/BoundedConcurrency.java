package com.hig.boilerplate.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <p>Semaphore를 사용하여 비동기 작업의 동시 실행 수를 제한하는 어노테이션입니다.</p>
 *
 * <p>
 * 이 어노테이션은 비동기 처리를 위한 {@link org.springframework.scheduling.annotation.Async @Async} 어노테이션과 함께 사용하도록 설계되었습니다.
 * {@code @Async}가 작업을 백그라운드 스레드에서 실행하도록 위임하면, {@code @BoundedConcurrency}는 그 작업의 진입로를 제어하는 '문지기' 역할을 합니다.
 * <br>
 * 메서드가 반환하는 {@link java.util.concurrent.CompletableFuture}의 완료 시점을 기준으로 Semaphore 허가를 자동으로 획득하고 반납하며,
 * 스레드 풀과 큐가 가득 찼을 때, 추가 요청은 Semaphore 허가를 받을 때까지 안전하게 대기합니다.
 * </p>
 *
 * <h3>사용법</h3>
 * <p>
 * 1. 제어하려는 비동기 메서드에 {@code @Async("myExecutor")}와 함께 이 어노테이션을 적용합니다.<br>
 * 2. {@code value} 속성에는 제어에 사용할 Spring bean으로 등록된 {@link java.util.concurrent.Semaphore}의 이름을 지정합니다.<br>
 * 3. 메서드는 반드시 {@code CompletableFuture<T>}를 반환해야 합니다.
 * </p>
 *
 * <pre><code>
 * // 1. Executor와 Semaphore를 Bean으로 등록합니다. (예: CustomExecutorProvider.java)
 * {@literal @Configuration}
 * public class MyExecutorConfig {
 *     {@literal @Bean("myTaskExecutor")}
 *     public Executor myTaskExecutor() {
 *         ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
 *         // ... executor 설정 ...
 *         executor.initialize();
 *         return executor;
 *     }
 *
 *     {@literal @Bean("myTaskSemaphore")}
 *     public Semaphore myTaskSemaphore() {
 *         // 최대 동시 실행 수 + 큐 용량
 *         return new Semaphore(100 + 500);
 *     }
 * }
 *
 * // 2. 서비스 메서드에 어노테이션을 적용합니다.
 * {@literal @Service}
 * public class MyService {
 *     {@literal @BoundedConcurrency("myTaskSemaphore")}
 *     {@literal @Async("myTaskExecutor")}
 *     public CompletableFuture<String/> doSomethingAsync(Long id) {
 *         // ... I/O Bound 작업 (CPU Bound 작업은 이점이 없습니다.) ...
 *         return CompletableFuture.completedFuture("결과");
 *     }
 * }
 *
 * // 3. 컨트롤러 혹은 서비스 등에서 서비스 메서드를 호출합니다.
 * {@literal @RestController} or {@literal @Service}
 * public class MyController {
 *     private final MyService myService;
 *
 *     // 생성자를 통해 MyService Bean을 주입받습니다.
 *     public MyController(MyService myService) {
 *         this.myService = myService;
 *     }
 *
 *     {@literal @GetMapping("/process/{id}")}
 *     public String processRequest({@literal @PathVariable} Long id) {
 *         // 비동기 메서드를 호출합니다. HTTP 요청 스레드는 즉시 반환됩니다.
 *         // 실제 작업은 'myTaskExecutor' 스레드 풀에서 실행됩니다.
 *         CompletableFuture<String/> future = myService.doSomethingAsync(id);
 *
 *         // 비동기 작업이 완료된 후 처리할 로직을 등록합니다. (Non-blocking 방식)
 *         future.thenAccept(result -> {
 *             log.info("비동기 작업 결과: {}", result);
 *         });
 *
 *         return "작업 요청이 성공적으로 접수되었습니다.";
 *     }
 * }
 * </code></pre>
 *
 * <h3>결과 처리 방식</h3>
 * <h4>1. 논블로킹(Non-blocking) 방식 (권장)</h4>
 * <p>
 * 비동기 작업이 완료된 후 실행될 콜백(callback)을 등록하는 방식입니다.
 * 호출 스레드를 차단하지 않아 시스템 리소스를 효율적으로 사용하므로 가장 권장됩니다.
 * </p>
 *
 * <h5>thenAccept: 결과를 소비만 할 때</h5>
 * <p>비동기 작업의 결과를 받아 처리하지만, 별도의 값을 반환하지 않을 때 사용합니다. (예: 로그 기록, 알림 발송)</p>
 * <pre><code>
 * CompletableFuture<String/> future = myService.doSomethingAsync(id);
 * future.thenAccept(result -> {
 *     log.info("비동기 작업이 성공적으로 완료되었습니다. 결과: {}", result);
 *     // 추가적인 반환 값 없음
 * });
 * </code></pre>
 *
 * <h5>thenApply: 결과를 변환하여 이어갈 때</h5>
 * <p>비동기 작업의 결과를 받아, 이를 가공하여 다른 타입의 결과로 변환하거나 추가적인 작업을 연결할 때 사용합니다.</p>
 * <pre><code>
 * CompletableFuture<Integer/> lengthFuture = myService.doSomethingAsync(id)
 *     .thenApply(result -> {
 *         // String 결과를 받아서 그 길이를 int로 변환하여 반환
 *         return result.length();
 *     });
 *
 * // lengthFuture는 이제 문자열의 길이를 담은 CompletableFuture가 됨
 * </code></pre>
 *
 * <h4>2. 블로킹(Blocking) 방식 (`.join()`)</h4>
 * <p>
 * {@code .join()}을 호출하여 비동기 작업이 완료될 때까지 현재 스레드를 대기시키고 결과를 직접 받을 수 있습니다.
 * 코드는 단순해지지만, 웹 컨트롤러와 같이 한정된 스레드로 동작하는 환경에서는 스레드 고갈을 유발할 수 있으므로
 * 사용에 각별한 주의가 필요합니다. (아래 예제 참고)
 * </p>
 * <pre><code>
 * String result = myService.doSomethingAsync(id).join(); // 현재 스레드가 여기서 멈춤
 * return "결과: " + result;
 * </code></pre>
 *
 * <h3>주의사항 (Self-Invocation)</h3>
 * <p>
 * Spring AOP의 프록시 기반 동작 방식 때문에, 이 어노테이션이 붙은 메서드를 같은 클래스 내의 다른 메서드에서
 * {@code this.methodName()} 형태로 직접 호출하면 AOP가 적용되지 않습니다. (self-invocation 문제)
 * 이 문제를 해결하려면, 해당 비동기 로직을 별도의 Spring Bean으로 분리하여 호출하는 것을 권장합니다.
 * </p>
 *
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface BoundedConcurrency {
    /**
     * 제어에 사용할 {@link java.util.concurrent.Semaphore} Spring Bean의 이름을 지정합니다.
     * @return Semaphore Bean의 이름
     */
    String value();
}


