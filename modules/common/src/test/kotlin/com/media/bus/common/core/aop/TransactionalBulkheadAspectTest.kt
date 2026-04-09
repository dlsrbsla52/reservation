package com.media.bus.common.core.aop

import com.media.bus.common.security.TokenProvider
import io.github.resilience4j.bulkhead.Bulkhead
import io.github.resilience4j.bulkhead.BulkheadFullException
import io.github.resilience4j.bulkhead.BulkheadRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.EnableAspectJAutoProxy
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.RestClient
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest(properties = [
    // Exposed 0.61.0 starter가 Spring Boot 3.x 구 패키지(autoconfigure.jdbc)를 참조하여
    // Spring Boot 4.x에서 ClassNotFoundException 발생 → ExposedAutoConfiguration 제외
    "spring.autoconfigure.exclude=org.jetbrains.exposed.v1.spring.boot4.autoconfigure.ExposedAutoConfiguration",
])
@TestPropertySource(properties = [
    "hig.bulkhead.database-name=orderDatabase",
    "resilience4j.bulkhead.instances.orderDatabase.maxConcurrentCalls=5",
    "resilience4j.bulkhead.instances.orderDatabase.maxWaitDuration=100ms",
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.liquibase.enabled=false",
    "jwt.secret=test-secret-key-for-unit-test-minimum-256bits-padding-here",
])
class TransactionalBulkheadAspectTest {

    // TokenProvider(JwtProvider)는 StringRedisTemplate과 jwt.secret에 의존한다.
    @MockitoBean
    private lateinit var tokenProvider: TokenProvider

    @Autowired
    private lateinit var testService: TestService

    @Autowired
    private lateinit var bulkheadRegistry: BulkheadRegistry

    private lateinit var bulkhead: Bulkhead

    @BeforeEach
    fun setUp() {
        bulkhead = bulkheadRegistry.bulkhead("orderDatabase")
    }

    @Test
    @DisplayName("Bulkhead 허용량을 초과하는 동시 요청은 차단되어야 한다")
    fun shouldRejectExcessiveCalls() {
        val maxCalls = 5
        val totalTaskCount = 10

        val latch = CountDownLatch(1)
        val futures = mutableListOf<Future<String>>()
        val successCount = AtomicInteger()
        val rejectionCount = AtomicInteger()

        // Virtual Thread Executor 사용
        Executors.newVirtualThreadPerTaskExecutor().use { executor ->
            for (i in 0 until totalTaskCount) {
                futures.add(executor.submit<String> {
                    try {
                        latch.await() // 시작 신호 대기
                        testService.longRunningTransaction()
                    } catch (e: BulkheadFullException) {
                        rejectionCount.incrementAndGet()
                        throw e
                    } catch (_: Exception) {
                        "Error"
                    }
                })
            }
            latch.countDown() // 동시 시작
        }
        // try-with-resources 구문에 의해 모든 태스크가 끝날 때까지 여기서 대기

        // 결과 검증
        for (future in futures) {
            try {
                val result = future.get()
                if ("Success" == result) {
                    successCount.incrementAndGet()
                }
            } catch (_: ExecutionException) {
                // BulkheadFullException은 여기서 잡힘
            }
        }

        assertThat(successCount.get()).isEqualTo(maxCalls)
        assertThat(rejectionCount.get()).isGreaterThan(0)

        // 퍼밋이 정상 반환되었는지 확인
        assertThat(bulkhead.metrics.availableConcurrentCalls).isEqualTo(maxCalls)
    }

    @Test
    @DisplayName("동일 스레드 내 중첩 트랜잭션 호출 시 퍼밋을 추가 점유하지 않아야 한다")
    fun shouldSupportReentrancy() {
        val initialPermits = bulkhead.metrics.availableConcurrentCalls
        testService.outerTransaction()
        assertThat(bulkhead.metrics.availableConcurrentCalls).isEqualTo(initialPermits)
    }

    @Test
    @DisplayName("비즈니스 예외 발생 시에도 퍼밋은 정상 반납되어야 한다")
    fun shouldReleasePermitOnException() {
        val initialPermits = bulkhead.metrics.availableConcurrentCalls
        assertThrows(RuntimeException::class.java) { testService.exceptionTransaction() }
        assertThat(bulkhead.metrics.availableConcurrentCalls).isEqualTo(initialPermits)
    }

    @TestConfiguration
    @EnableAspectJAutoProxy
    class TestConfig {
        // Spring Boot 4에서 RestClient.Builder 자동 구성이 변경되어 테스트 컨텍스트에서 누락됨.
        @Bean
        fun restClientBuilder(): RestClient.Builder = RestClient.builder()

        @Bean
        fun testService(): TestService = TestService()
    }

    open class TestService {

        @Autowired
        @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
        private lateinit var testServiceProvider: ObjectProvider<TestService>

        @Transactional
        open fun longRunningTransaction(): String {
            try {
                Thread.sleep(200)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            return "Success"
        }

        @Transactional
        open fun outerTransaction() {
            Objects.requireNonNull(testServiceProvider.getObject()).innerTransaction()
        }

        @Transactional
        open fun innerTransaction() {
            // 내부 로직
        }

        @Transactional
        open fun exceptionTransaction() {
            throw RuntimeException("Business Error")
        }
    }
}
