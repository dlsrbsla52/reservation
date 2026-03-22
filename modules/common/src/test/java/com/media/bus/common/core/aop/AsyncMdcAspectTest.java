package com.media.bus.common.core.aop;

import com.media.bus.common.logging.MdcContextUtil;
import com.media.bus.common.security.TokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.Async;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
    "hig.bulkhead.database-name=orderDatabase",
    "resilience4j.bulkhead.instances.orderDatabase.maxConcurrentCalls=5",
    "resilience4j.bulkhead.instances.orderDatabase.maxWaitDuration=100ms",
    "spring.datasource.url=jdbc:h2:mem:asyncmdctest;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.liquibase.enabled=false",
    "jwt.secret=test-secret-key-for-unit-test-minimum-256bits-padding-here"
})
@DisplayName("AsyncMdcAspect")
public class AsyncMdcAspectTest {

    // TokenProvider(JwtProvider)는 StringRedisTemplate과 jwt.secret에 의존합니다.
    // @MockitoBean으로 등록하여 Redis/JWT 인프라 없이 ApplicationContext가 올라오도록 합니다.
    @MockitoBean
    private TokenProvider tokenProvider;

    @Autowired
    private AsyncTestService asyncTestService;

    @BeforeEach void setUp()    { MDC.clear(); }
    @AfterEach  void tearDown() { MDC.clear(); }

    @Nested
    @DisplayName("@Async MDC 전파")
    class AsyncMdcPropagationTest {

        @Test
        @DisplayName("호출 스레드의 MDC가 비동기 실행 스레드로 전파된다")
        void shouldPropagateMdcToAsyncThread() throws Exception {
            MDC.put("requestId", "async-test-req-001");
            MDC.put("userId", "async-test-user");

            Map<String, String> asyncMdc = asyncTestService.captureMdc()
                .get(5, TimeUnit.SECONDS);

            assertThat(asyncMdc).containsEntry("requestId", "async-test-req-001")
                                .containsEntry("userId",    "async-test-user");
        }

        @Test
        @DisplayName("MDC가 없는 환경(@Scheduled, 이벤트 리스너 등)에서도 안전하게 실행된다")
        void shouldHandleEmptyMdcSafely() throws Exception {
            // MDC 비어있는 상태 — HTTP 컨텍스트 없는 배치/스케줄러 시뮬레이션
            Map<String, String> asyncMdc = asyncTestService.captureMdc()
                .get(5, TimeUnit.SECONDS);

            assertThat(asyncMdc).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("@Async 완료 후 호출 스레드의 MDC가 원래 상태로 복원된다")
        void shouldRestoreCallerThreadMdcAfterAsyncCall() throws Exception {
            MDC.put("requestId", "caller-req-restore-test");
            MDC.put("userId",    "caller-user");

            asyncTestService.captureMdc().get(5, TimeUnit.SECONDS);

            // Aspect finally 블록이 호출 스레드 MDC를 복원해야 한다
            assertThat(MDC.get("requestId")).isEqualTo("caller-req-restore-test");
            assertThat(MDC.get("userId")).isEqualTo("caller-user");
        }
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        public AsyncTestService asyncTestService() {
            return new AsyncTestService();
        }
    }

    /**
     * @Async 실행 스레드에서 MDC를 캡처하여 반환하는 테스트용 서비스.
     * executor 미지정 → AsyncConfigurer.getAsyncExecutor() (IoBoundExecutor) 사용
     */
    static class AsyncTestService {
        @Async
        public CompletableFuture<Map<String, String>> captureMdc() {
            return CompletableFuture.completedFuture(MdcContextUtil.capture());
        }
    }
}
