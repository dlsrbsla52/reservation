package com.media.bus.common.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MdcContextUtil")
class MdcContextUtilTest {

    @BeforeEach void setUp()    { MDC.clear(); }
    @AfterEach  void tearDown() { MDC.clear(); }

    @Nested
    @DisplayName("capture()")
    class CaptureTest {

        @Test
        @DisplayName("MDC가 비어있을 때 빈 Map을 반환한다")
        void returnsEmptyMapWhenMdcIsEmpty() {
            Map<String, String> result = MdcContextUtil.capture();
            assertThat(result).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("현재 MDC 내용의 복사본을 반환한다")
        void returnsCopyOfCurrentMdc() {
            MDC.put("requestId", "req-001");
            MDC.put("userId", "user-001");

            Map<String, String> captured = MdcContextUtil.capture();

            assertThat(captured)
                .containsEntry("requestId", "req-001")
                .containsEntry("userId", "user-001");
        }

        @Test
        @DisplayName("반환된 Map을 변경해도 원본 MDC에 영향을 주지 않는다")
        void capturedMapIsIndependentFromOriginal() {
            MDC.put("requestId", "req-original");
            Map<String, String> captured = MdcContextUtil.capture();

            // 원본 MDC 변경
            MDC.put("requestId", "req-modified");

            // 복사본은 그대로여야 한다
            assertThat(captured.get("requestId")).isEqualTo("req-original");
            // 원본은 변경되었다
            assertThat(MDC.get("requestId")).isEqualTo("req-modified");
        }
    }

    @Nested
    @DisplayName("wrap(Runnable)")
    class WrapRunnableTest {

        @Test
        @DisplayName("호출 시점 MDC가 실행 스레드로 전파된다")
        void propagatesMdcToNewThread() throws Exception {
            MDC.put("requestId", "req-runnable-001");
            MDC.put("userId", "user-runnable");
            AtomicReference<String> capturedReqId  = new AtomicReference<>();
            AtomicReference<String> capturedUserId = new AtomicReference<>();

            CompletableFuture.runAsync(MdcContextUtil.wrap(() -> {
                capturedReqId.set(MDC.get("requestId"));
                capturedUserId.set(MDC.get("userId"));
            })).get();

            assertThat(capturedReqId.get()).isEqualTo("req-runnable-001");
            assertThat(capturedUserId.get()).isEqualTo("user-runnable");
        }

        @Test
        @DisplayName("실행 완료 후 실행 스레드의 이전 MDC가 복원된다 — 스레드 풀 재사용 시 오염 방지")
        void restoresPreviousContextOfWorkerThread() throws InterruptedException {
            MDC.put("callerKey", "callerValue");
            AtomicReference<String> afterRun = new AtomicReference<>();

            Thread worker = Thread.ofVirtual().start(() -> {
                // 실행 스레드가 이미 자신의 MDC를 가지고 있는 상황
                MDC.put("threadKey", "threadValue");
                // wrap 실행
                MdcContextUtil.wrap((Runnable) () -> { /* callerKey=callerValue 주입됨 */ }).run();
                // wrap 완료 후 실행 스레드 MDC가 복원되어야 한다
                afterRun.set(MDC.get("threadKey"));
                MDC.clear();
            });
            worker.join();

            assertThat(afterRun.get()).isEqualTo("threadValue");
        }

        @Test
        @DisplayName("MDC가 빈 상태에서도 안전하게 실행된다")
        void handlesEmptyMdcSafely() throws Exception {
            AtomicReference<Map<String, String>> capturedMdc = new AtomicReference<>();

            CompletableFuture.runAsync(MdcContextUtil.wrap(() ->
                capturedMdc.set(MdcContextUtil.capture())
            )).get();

            assertThat(capturedMdc.get()).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("Runnable 실행 중 예외가 발생해도 이전 MDC가 복원된다")
        void restoresContextEvenOnException() throws InterruptedException {
            AtomicReference<String> afterException = new AtomicReference<>();

            Thread worker = Thread.ofVirtual().start(() -> {
                MDC.put("existingKey", "existingValue");
                try {
                    MdcContextUtil.wrap((Runnable) () -> {
                        throw new RuntimeException("test error");
                    }).run();
                } catch (RuntimeException ignored) {
                    afterException.set(MDC.get("existingKey"));
                } finally {
                    MDC.clear();
                }
            });
            worker.join();

            assertThat(afterException.get()).isEqualTo("existingValue");
        }
    }

    @Nested
    @DisplayName("wrap(Callable)")
    class WrapCallableTest {

        @Test
        @DisplayName("호출 시점 MDC가 Callable 실행 스레드로 전파된다")
        void propagatesMdcToCallable() throws Exception {
            MDC.put("requestId", "req-callable-001");
            AtomicReference<String> capturedReqId = new AtomicReference<>();

            CompletableFuture.supplyAsync(asSupplier(MdcContextUtil.wrap(() -> {
                capturedReqId.set(MDC.get("requestId"));
                return "ok";
            }))).get();

            assertThat(capturedReqId.get()).isEqualTo("req-callable-001");
        }

        @Test
        @DisplayName("Callable의 반환값이 정상 전달된다")
        void returnsCallableResult() throws Exception {
            String result = CompletableFuture.supplyAsync(
                asSupplier(MdcContextUtil.wrap(() -> "expected-result"))
            ).get();

            assertThat(result).isEqualTo("expected-result");
        }

        @Test
        @DisplayName("Callable 실행 중 예외가 발생해도 이전 MDC가 복원된다")
        void restoresContextEvenOnException() throws InterruptedException {
            AtomicReference<String> afterException = new AtomicReference<>();

            Thread worker = Thread.ofVirtual().start(() -> {
                MDC.put("existingKey", "existingValue");
                try {
                    MdcContextUtil.wrap(() -> { throw new RuntimeException("callable error"); }).call();
                } catch (Exception ignored) {
                    afterException.set(MDC.get("existingKey"));
                } finally {
                    MDC.clear();
                }
            });
            worker.join();

            assertThat(afterException.get()).isEqualTo("existingValue");
        }

        private static <T> Supplier<T> asSupplier(java.util.concurrent.Callable<T> callable) {
            return () -> {
                try { return callable.call(); }
                catch (Exception e) { throw new RuntimeException(e); }
            };
        }
    }
}
