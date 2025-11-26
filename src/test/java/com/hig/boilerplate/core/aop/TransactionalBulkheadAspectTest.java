package com.hig.boilerplate.core.aop;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@TestPropertySource(properties = {
    "resilience4j.bulkhead.instances.orderDatabase.maxConcurrentCalls=5",
    "resilience4j.bulkhead.instances.orderDatabase.maxWaitDuration=100ms",
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.liquibase.enabled=false" // 테스트 속도를 위해 Liquibase 비활성화 (필요시 true)
})
public class TransactionalBulkheadAspectTest {

    @Autowired
    private TestService testService;

    @Autowired
    private BulkheadRegistry bulkheadRegistry;

    private Bulkhead bulkhead;

    @BeforeEach
    void setUp() {
        bulkhead = bulkheadRegistry.bulkhead("orderDatabase");
    }

    @Test
    @DisplayName("Bulkhead 허용량을 초과하는 동시 요청은 차단되어야 한다")
    void shouldRejectExcessiveCalls() throws InterruptedException {
        int maxCalls = 5;
        int totalTaskCount = 10;

        CountDownLatch latch = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger rejectionCount = new AtomicInteger();

        // Virtual Thread Executor 사용
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < totalTaskCount; i++) {
                futures.add(executor.submit(() -> {
                    try {
                        latch.await(); // 시작 신호 대기
                        return testService.longRunningTransaction();
                    } catch (BulkheadFullException e) {
                        rejectionCount.incrementAndGet();
                        throw e;
                    } catch (Exception e) {
                        return "Error";
                    }
                }));
            }
            latch.countDown(); // 동시 시작!
        }
        // try-with-resources 구문에 의해 모든 태스크가 끝날 때까지 여기서 대기합니다.

        // 결과 검증
        for (Future<String> future : futures) {
            try {
                String result = future.get();
                if ("Success".equals(result)) {
                    successCount.incrementAndGet();
                }
            } catch (ExecutionException e) {
                // BulkheadFullException은 여기서 잡힙니다.
            }
        }

        assertThat(successCount.get()).isEqualTo(maxCalls);
        assertThat(rejectionCount.get()).isGreaterThan(0);

        // 퍼밋이 정상 반환되었는지 확인
        assertThat(bulkhead.getMetrics().getAvailableConcurrentCalls()).isEqualTo(maxCalls);
    }

    @Test
    @DisplayName("동일 스레드 내 중첩 트랜잭션 호출 시 퍼밋을 추가 점유하지 않아야 한다")
    void shouldSupportReentrancy() {
        int initialPermits = bulkhead.getMetrics().getAvailableConcurrentCalls();
        testService.outerTransaction();
        assertThat(bulkhead.getMetrics().getAvailableConcurrentCalls()).isEqualTo(initialPermits);
    }

    @Test
    @DisplayName("비즈니스 예외 발생 시에도 퍼밋은 정상 반납되어야 한다")
    void shouldReleasePermitOnException() {
        int initialPermits = bulkhead.getMetrics().getAvailableConcurrentCalls();
        assertThrows(RuntimeException.class, () -> testService.exceptionTransaction());
        assertThat(bulkhead.getMetrics().getAvailableConcurrentCalls()).isEqualTo(initialPermits);
    }

    @TestConfiguration
    @EnableAspectJAutoProxy
    static class TestConfig {
        @Bean
        public TestService testService() {
            return new TestService();
        }
    }

    static class TestService {

        @Autowired
        @SuppressWarnings("null")
        private ObjectProvider<TestService> testServiceProvider;

        @Transactional
        public String longRunningTransaction() {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "Success";
        }

        @Transactional
        public void outerTransaction() {
            Objects.requireNonNull(testServiceProvider.getObject()).innerTransaction();
        }

        @Transactional
        public void innerTransaction() {
            // 내부 로직
        }

        @Transactional
        public void exceptionTransaction() {
            throw new RuntimeException("Business Error");
        }
    }
}