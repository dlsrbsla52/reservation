package com.media.bus.common.core.aop

import com.media.bus.common.logging.MdcContextUtil
import com.media.bus.common.security.TokenProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.EnableAspectJAutoProxy
import org.springframework.scheduling.annotation.Async
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.web.client.RestClient
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * Spring Boot 4에서 @Nested 클래스 컨텍스트가 외부 클래스의 @TestConfiguration을 로드하지 않는
 * Breaking Change로 인해 @Nested 구조를 제거하고 평탄화함.
 */
@SpringBootTest(properties = [
    // Exposed 0.61.0 starter가 Spring Boot 3.x 구 패키지를 참조 → Spring Boot 4.x에서 ClassNotFoundException
    "spring.autoconfigure.exclude=org.jetbrains.exposed.v1.spring.boot4.autoconfigure.ExposedAutoConfiguration",
])
@TestPropertySource(properties = [
    "hig.bulkhead.database-name=orderDatabase",
    "resilience4j.bulkhead.instances.orderDatabase.maxConcurrentCalls=5",
    "resilience4j.bulkhead.instances.orderDatabase.maxWaitDuration=100ms",
    "spring.datasource.url=jdbc:h2:mem:asyncmdctest;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.liquibase.enabled=false",
    "jwt.secret=test-secret-key-for-unit-test-minimum-256bits-padding-here",
])
@DisplayName("AsyncMdcAspect")
class AsyncMdcAspectTest {

    // TokenProvider(JwtProvider)는 StringRedisTemplate과 jwt.secret에 의존한다.
    @MockitoBean
    private lateinit var tokenProvider: TokenProvider

    // RestClientConfig.internalRestClient(RestClient.Builder) 팩토리 메서드 호출을 건너뛰기 위해 선점 Mock 등록
    @MockitoBean
    private lateinit var internalRestClient: RestClient

    @Autowired
    private lateinit var asyncTestService: AsyncTestService

    @BeforeEach fun setUp() { MDC.clear() }
    @AfterEach fun tearDown() { MDC.clear() }

    @Test
    @DisplayName("@Async MDC 전파 — 호출 스레드의 MDC가 비동기 실행 스레드로 전파된다")
    fun shouldPropagateMdcToAsyncThread() {
        MDC.put("requestId", "async-test-req-001")
        MDC.put("memberId", "async-test-user")

        val asyncMdc = asyncTestService.captureMdc()
            .get(5, TimeUnit.SECONDS)

        assertThat(asyncMdc).containsEntry("requestId", "async-test-req-001")
            .containsEntry("memberId", "async-test-user")
    }

    @Test
    @DisplayName("@Async MDC 전파 — MDC가 없는 환경(@Scheduled, 이벤트 리스너 등)에서도 안전하게 실행된다")
    fun shouldHandleEmptyMdcSafely() {
        // MDC 비어있는 상태 — HTTP 컨텍스트 없는 배치/스케줄러 시뮬레이션
        val asyncMdc = asyncTestService.captureMdc()
            .get(5, TimeUnit.SECONDS)

        assertThat(asyncMdc).isNotNull.isEmpty()
    }

    @Test
    @DisplayName("@Async MDC 전파 — @Async 완료 후 호출 스레드의 MDC가 원래 상태로 복원된다")
    fun shouldRestoreCallerThreadMdcAfterAsyncCall() {
        MDC.put("requestId", "caller-req-restore-test")
        MDC.put("memberId", "caller-user")

        asyncTestService.captureMdc().get(5, TimeUnit.SECONDS)

        // Aspect finally 블록이 호출 스레드 MDC를 복원해야 한다
        assertThat(MDC.get("requestId")).isEqualTo("caller-req-restore-test")
        assertThat(MDC.get("memberId")).isEqualTo("caller-user")
    }

    @TestConfiguration
    @EnableAspectJAutoProxy
    class TestConfig {
        @Bean
        fun asyncTestService(): AsyncTestService = AsyncTestService()
    }

    /** @Async 실행 스레드에서 MDC를 캡처하여 반환하는 테스트용 서비스. */
    open class AsyncTestService {
        @Async
        open fun captureMdc(): CompletableFuture<Map<String, String>> =
            CompletableFuture.completedFuture(MdcContextUtil.capture())
    }
}
