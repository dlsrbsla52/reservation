package com.media.bus.common.logging

import org.apache.logging.log4j.ThreadContext
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch

/**
 * ## MdcLoggingFilter 실사용 패턴 시각화 데모
 *
 * ### 실행 방법
 * IDE에서 main() 왼쪽 ▶ 버튼 클릭.
 *
 * ### 보여주는 것
 *   1. HTTP 요청이 들어오면 필터가 MDC에 requestId/memberId를 주입한다
 *   2. 컨트롤러 -> 서비스 -> 레포지토리 계층에서 로거를 호출하기만 해도
 *     모든 로그에 같은 requestId/memberId가 자동으로 붙는다
 *   3. 비인증 요청에서는 memberId 필드가 없다
 *   4. 예외가 발생해도 요청이 끝나면 MDC가 깨끗하게 정리된다
 *   5. 새 스레드를 만들면 MDC가 전파되지 않고, 명시적 복사로 해결한다
 *
 * **핵심:** 개발자는 MDC를 직접 건드리지 않는다.
 * 로거를 평소처럼 쓰기만 하면 필터와 Micrometer Tracing이 모든 context 필드를 채운다.
 */
object MdcLoggingFilterDemo {

    // 실제 코드에서는 각 클래스에 선언한다. 예: LoggerFactory.getLogger(StopController::class.java)
    private val filter = LoggerFactory.getLogger("MdcLoggingFilter")
    private val controller = LoggerFactory.getLogger("StopController")
    private val service = LoggerFactory.getLogger("StopService")
    private val repository = LoggerFactory.getLogger("StopRepository")
    private val async = LoggerFactory.getLogger("AsyncTask")

    @JvmStatic
    fun main(args: Array<String>) {

        // 시나리오 1: 인증된 사용자 요청
        divider("시나리오 1 — 인증 사용자 요청 (X-Request-ID 헤더 있음)")

        simulateRequest("front-abc-123-def-456-789", "user-9f4e2a1b") {
            controller.info("getStop() 호출 — stopId=42")
            service.info("StopService.findById() 실행")
            repository.debug("SELECT * FROM stop.stop WHERE id = ? 쿼리 실행")
            service.info("정류장 조회 완료 — name=강남역 버스정류소")
            controller.info("HTTP 200 OK 반환")
        }

        sleep()

        // 시나리오 2: 비인증 요청
        divider("시나리오 2 — 비인증 요청 (memberId 없음, requestId 자동 생성)")

        simulateRequest(null, null) {
            controller.info("healthCheck() 호출")
            controller.info("HTTP 200 OK 반환")
        }

        sleep()

        // 시나리오 3: 예외 발생
        divider("시나리오 3 — 예외 발생 → 요청 후 MDC 정리 확인")

        try {
            simulateRequest("req-xyz-error-001-abc-def", "user-error-case") {
                service.info("ReservationService.reserve() 실행 — seatId=5A")
                repository.debug("SELECT ... FOR UPDATE 실행")
                service.warn("좌석 5A 이미 예약됨, 충돌 감지")
                throw RuntimeException("SeatAlreadyReservedException: seat=5A")
            }
        } catch (_: RuntimeException) {
            // ExceptionAdvisor가 잡아서 ErrorView 반환
        }

        println()
        println("  ✅ 예외 후 ThreadContext.requestId = \"${ThreadContext.get("requestId")}\"  (null이어야 정상)")
        println("  ✅ 예외 후 ThreadContext.memberId    = \"${ThreadContext.get("memberId")}\"  (null이어야 정상)")
        println()

        sleep()

        // 시나리오 4: 비동기 스레드 — MDC 전파 문제와 해결책
        divider("시나리오 4 — 비동기 스레드 MDC 전파")

        simulateRequest("req-async-demo-uuid-0001", "user-async") {
            service.info("동기 로직 실행 — MDC 정상")

            println("\n  ── ❌ 잘못된 방법 (MDC 복사 없음) ──\n")
            CompletableFuture.runAsync {
                async.warn("새 스레드 — requestId가 비어있음: \"{}\"", ThreadContext.get("requestId"))
            }.join()

            println("\n  ── ✅ 올바른 방법 (MDC 스냅샷 복사) ──\n")
            val mdcSnapshot = ThreadContext.getImmutableContext()

            CompletableFuture.runAsync {
                ThreadContext.putAll(mdcSnapshot)
                try {
                    async.info("새 스레드 — requestId 정상 전파됨: {}", ThreadContext.get("requestId"))
                    async.info("외부 API 호출 완료")
                } finally {
                    ThreadContext.clearAll()
                }
            }.join()

            service.info("비동기 작업 완료 후 동기 로직 재개 — MDC 여전히 유지")
        }

        sleep()

        // 시나리오 5: Virtual Thread 핵심 검증
        divider("시나리오 5 — Virtual Thread 핵심 검증")

        // [5-1] Park/Resume
        println("  [5-1] VT park/resume — carrier thread 반납 후에도 MDC 유지 확인\n")

        Thread.ofVirtual().name("vt-request-handler").start {
            ThreadContext.put("requestId", "vt-park-resume-req-001")
            ThreadContext.put("memberId", "vt-user-park")
            ThreadContext.put("traceId", "parktrace00001a")

            service.info("VT 실행 — IO 호출 직전 (carrier thread 점유 중)")

            try { Thread.sleep(30) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }

            service.info("VT 재개 — carrier thread 재할당 후 (MDC 유지 ✅)")
            ThreadContext.clearAll()
        }.join()

        sleep()

        // [5-2] 동시 VT 격리
        println("\n  [5-2] 동시 VT 격리 — 각 VT의 requestId가 독립적으로 유지되는지 확인")
        println("        각 줄의 req= 값이 vt-N-req 형식으로 뒤섞이지 않아야 한다\n")

        val vtCount = 6
        val startGate = CountDownLatch(1)
        val done = CountDownLatch(vtCount)

        for (i in 1..vtCount) {
            val reqId = "vt-$i-req-concurrent"
            val traceId = "conctrace0000$i"
            Thread.ofVirtual().name("vt-concurrent-$i").start {
                try {
                    startGate.await()
                    ThreadContext.put("requestId", reqId)
                    ThreadContext.put("traceId", traceId)

                    Thread.sleep((Math.random() * 30).toLong())

                    service.info("동시 VT 재개 — 자신의 req= 값을 확인하세요: {}", ThreadContext.get("requestId"))
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                } finally {
                    ThreadContext.clearAll()
                    done.countDown()
                }
            }
        }
        startGate.countDown()
        done.await()

        sleep()

        // [5-3] 자식 VT MDC 미상속
        println("\n  [5-3] 자식 VT — MDC 미상속 문제와 해결책\n")

        Thread.ofVirtual().name("vt-parent").start {
            ThreadContext.put("requestId", "vt-parent-req-9999")
            ThreadContext.put("traceId", "parenttrace0001")

            service.info("부모 VT — 자식 VT 2개 생성 직전")

            println("\n    ── ❌ 자식 VT (복사 없음) ──\n")
            val childNoMdc = Thread.ofVirtual().name("vt-child-no-mdc").start {
                async.warn("자식 VT — requestId 비어있음: \"{}\" ❌", ThreadContext.get("requestId"))
            }
            try { childNoMdc.join() } catch (_: InterruptedException) { Thread.currentThread().interrupt() }

            println("\n    ── ✅ 자식 VT (MDC 스냅샷 복사) ──\n")
            val snapshot = ThreadContext.getImmutableContext()

            val childWithMdc = Thread.ofVirtual().name("vt-child-with-mdc").start {
                ThreadContext.putAll(snapshot)
                try {
                    async.info("자식 VT — requestId 정상: \"{}\" ✅", ThreadContext.get("requestId"))
                } finally {
                    ThreadContext.clearAll()
                }
            }
            try { childWithMdc.join() } catch (_: InterruptedException) { Thread.currentThread().interrupt() }

            service.info("부모 VT — 자식 완료 후 자신의 MDC 여전히 유지")
            ThreadContext.clearAll()
        }.join()

        sleep()

        divider("실제 코드에서 사용법")
        printUsageGuide()
    }

    // ── 내부 시뮬레이터 ───────────────────────────────────────────────────────

    private fun simulateRequest(requestId: String?, memberId: String?, logic: Runnable) {
        val resolvedId = if (!requestId.isNullOrBlank()) requestId else UUID.randomUUID().toString()

        ThreadContext.put("requestId", resolvedId)
        if (memberId != null) {
            ThreadContext.put("memberId", memberId)
        }
        ThreadContext.put("traceId", UUID.randomUUID().toString().replace("-", "").substring(0, 16))

        filter.info("── 요청 진입 ── MDC 주입 완료: requestId={}, memberId={}", resolvedId, memberId)

        try {
            logic.run()
        } finally {
            filter.info("── 요청 종료 ── MDC 정리")
            ThreadContext.remove("requestId")
            ThreadContext.remove("memberId")
            ThreadContext.remove("traceId")
        }
    }

    private fun divider(title: String) {
        println("\n\n┌────────────────────────────────────────────────────────────────────────────┐")
        println("│  %-74s│".format(title))
        println("└────────────────────────────────────────────────────────────────────────────┘\n")
    }

    private fun sleep() {
        Thread.sleep(50)
    }

    private fun printUsageGuide() {
        println("""
                ┌─ 개발자가 작성하는 코드 ───────────────────────────────────────────────────────────┐
                │                                                                              │
                │  // 1. 각 클래스에 Logger 선언 (평소와 동일)                                        │
                │  private val log = LoggerFactory.getLogger(MyService::class.java)            │
                │                                                                              │
                │  // 2. 로그 호출 (MDC 건드리지 않아도 requestId, memberId가 자동으로 붙음)               │
                │  log.info("처리 시작 — itemId={}", itemId)                                    │
                │  log.debug("DB 쿼리 실행")                                                    │
                │  log.error("처리 실패", exception)                                            │
                │                                                                              │
                │  // 3. @Async / CompletableFuture 사용 시에만 MDC 수동 복사                      │
                │  val mdc = MDC.getCopyOfContextMap()                                        │
                │  CompletableFuture.runAsync {                                                │
                │      MDC.setContextMap(mdc)                                                  │
                │      try { log.info("비동기 작업") } finally { MDC.clear() }              │
                │  }                                                                           │
                │                                                                              │
                │  ↑ 이것이 전부다. MDC.put(), requestId 생성 등은 모두 필터가 담당한다.     │
                │                                                                              │
                └──────────────────────────────────────────────────────────────────────────────┘

                ┌─ CloudWatch Logs Insights 쿼리 예시 ───────────────────────────────────────┐
                │                                                                              │
                │  # 특정 요청의 전체 로그 추적                                              │
                │  fields @timestamp, level, message, memberId                                  │
                │  | filter requestId = "front-abc-123-def-456-789"                           │
                │  | sort @timestamp asc                                                       │
                │                                                                              │
                │  # 에러 로그에서 traceId로 분산 추적                                      │
                │  fields @timestamp, level, message, requestId, service                      │
                │  | filter level = "ERROR"                                                    │
                │  | filter traceId = "a1b2c3d4e5f60001"                                     │
                │                                                                              │
                └──────────────────────────────────────────────────────────────────────────────┘
        """.trimIndent())
    }
}
