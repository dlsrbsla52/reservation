package com.media.bus.common.logging

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.slf4j.MDC
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Supplier

@DisplayName("MdcContextUtil")
class MdcContextUtilTest {

    @BeforeEach fun setUp() { MDC.clear() }
    @AfterEach fun tearDown() { MDC.clear() }

    @Nested
    @DisplayName("capture()")
    inner class CaptureTest {

        @Test
        @DisplayName("MDC가 비어있을 때 빈 Map을 반환한다")
        fun returnsEmptyMapWhenMdcIsEmpty() {
            val result = MdcContextUtil.capture()
            assertThat(result).isNotNull.isEmpty()
        }

        @Test
        @DisplayName("현재 MDC 내용의 복사본을 반환한다")
        fun returnsCopyOfCurrentMdc() {
            MDC.put("requestId", "req-001")
            MDC.put("memberId", "user-001")

            val captured = MdcContextUtil.capture()

            assertThat(captured)
                .containsEntry("requestId", "req-001")
                .containsEntry("memberId", "user-001")
        }

        @Test
        @DisplayName("반환된 Map을 변경해도 원본 MDC에 영향을 주지 않는다")
        fun capturedMapIsIndependentFromOriginal() {
            MDC.put("requestId", "req-original")
            val captured = MdcContextUtil.capture()

            // 원본 MDC 변경
            MDC.put("requestId", "req-modified")

            // 복사본은 그대로여야 한다
            assertThat(captured["requestId"]).isEqualTo("req-original")
            // 원본은 변경되었다
            assertThat(MDC.get("requestId")).isEqualTo("req-modified")
        }
    }

    @Nested
    @DisplayName("wrap(Runnable)")
    inner class WrapRunnableTest {

        @Test
        @DisplayName("호출 시점 MDC가 실행 스레드로 전파된다")
        fun propagatesMdcToNewThread() {
            MDC.put("requestId", "req-runnable-001")
            MDC.put("memberId", "user-runnable")
            val capturedReqId = AtomicReference<String>()
            val capturedMemberId = AtomicReference<String>()

            CompletableFuture.runAsync(MdcContextUtil.wrap(Runnable {
                capturedReqId.set(MDC.get("requestId"))
                capturedMemberId.set(MDC.get("memberId"))
            })).get()

            assertThat(capturedReqId.get()).isEqualTo("req-runnable-001")
            assertThat(capturedMemberId.get()).isEqualTo("user-runnable")
        }

        @Test
        @DisplayName("실행 완료 후 실행 스레드의 이전 MDC가 복원된다 — 스레드 풀 재사용 시 오염 방지")
        fun restoresPreviousContextOfWorkerThread() {
            MDC.put("callerKey", "callerValue")
            val afterRun = AtomicReference<String>()

            val worker = Thread.ofVirtual().start {
                // 실행 스레드가 이미 자신의 MDC를 가지고 있는 상황
                MDC.put("threadKey", "threadValue")
                // wrap 실행
                MdcContextUtil.wrap(Runnable { /* callerKey=callerValue 주입됨 */ }).run()
                // wrap 완료 후 실행 스레드 MDC가 복원되어야 한다
                afterRun.set(MDC.get("threadKey"))
                MDC.clear()
            }
            worker.join()

            assertThat(afterRun.get()).isEqualTo("threadValue")
        }

        @Test
        @DisplayName("MDC가 빈 상태에서도 안전하게 실행된다")
        fun handlesEmptyMdcSafely() {
            val capturedMdc = AtomicReference<Map<String, String>>()

            CompletableFuture.runAsync(MdcContextUtil.wrap(Runnable {
                capturedMdc.set(MdcContextUtil.capture())
            })).get()

            assertThat(capturedMdc.get()).isNotNull.isEmpty()
        }

        @Test
        @DisplayName("Runnable 실행 중 예외가 발생해도 이전 MDC가 복원된다")
        fun restoresContextEvenOnException() {
            val afterException = AtomicReference<String>()

            val worker = Thread.ofVirtual().start {
                MDC.put("existingKey", "existingValue")
                try {
                    MdcContextUtil.wrap(Runnable {
                        throw RuntimeException("test error")
                    }).run()
                } catch (_: RuntimeException) {
                    afterException.set(MDC.get("existingKey"))
                } finally {
                    MDC.clear()
                }
            }
            worker.join()

            assertThat(afterException.get()).isEqualTo("existingValue")
        }
    }

    @Nested
    @DisplayName("wrap(Callable)")
    inner class WrapCallableTest {

        @Test
        @DisplayName("호출 시점 MDC가 Callable 실행 스레드로 전파된다")
        fun propagatesMdcToCallable() {
            MDC.put("requestId", "req-callable-001")
            val capturedReqId = AtomicReference<String>()

            CompletableFuture.supplyAsync(asSupplier(MdcContextUtil.wrap(Callable {
                capturedReqId.set(MDC.get("requestId"))
                "ok"
            }))).get()

            assertThat(capturedReqId.get()).isEqualTo("req-callable-001")
        }

        @Test
        @DisplayName("Callable의 반환값이 정상 전달된다")
        fun returnsCallableResult() {
            val result = CompletableFuture.supplyAsync(
                asSupplier(MdcContextUtil.wrap(Callable { "expected-result" }))
            ).get()

            assertThat(result).isEqualTo("expected-result")
        }

        @Test
        @DisplayName("Callable 실행 중 예외가 발생해도 이전 MDC가 복원된다")
        fun restoresContextEvenOnException() {
            val afterException = AtomicReference<String>()

            val worker = Thread.ofVirtual().start {
                MDC.put("existingKey", "existingValue")
                try {
                    MdcContextUtil.wrap(Callable<Any> { throw RuntimeException("callable error") }).call()
                } catch (_: Exception) {
                    afterException.set(MDC.get("existingKey"))
                } finally {
                    MDC.clear()
                }
            }
            worker.join()

            assertThat(afterException.get()).isEqualTo("existingValue")
        }

        private fun <T> asSupplier(callable: Callable<T>): Supplier<T> = Supplier {
            try {
                callable.call()
            } catch (e: Exception) {
                throw RuntimeException(e)
            }
        }
    }
}
