package com.media.bus.common.logging;

import org.apache.logging.log4j.ThreadContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

/**
 * MdcLoggingFilter 실사용 패턴 시각화 데모.
 *
 * <h2>실행 방법</h2>
 * IDE에서 main() 왼쪽 ▶ 버튼 클릭.
 *
 * <h2>보여주는 것</h2>
 * <ol>
 *   <li>HTTP 요청이 들어오면 필터가 MDC에 requestId/userId를 주입한다</li>
 *   <li>컨트롤러 → 서비스 → 레포지토리 계층에서 로거를 호출하기만 해도
 *       모든 로그에 같은 requestId/userId가 자동으로 붙는다</li>
 *   <li>비인증 요청에서는 userId 필드가 없다</li>
 *   <li>예외가 발생해도 요청이 끝나면 MDC가 깨끗하게 정리된다</li>
 *   <li>새 스레드를 만들면 MDC가 전파되지 않고, 명시적 복사로 해결한다</li>
 * </ol>
 *
 * <p><b>핵심:</b> 개발자는 MDC를 직접 건드리지 않는다.
 * 로거를 평소처럼 쓰기만 하면 필터와 Micrometer Tracing이 모든 context 필드를 채운다.
 */
public class MdcLoggingFilterDemo {

    // 실제 코드에서는 각 클래스에 선언한다. 예: LoggerFactory.getLogger(StopController.class)
    private static final Logger filter     = LoggerFactory.getLogger("MdcLoggingFilter");
    private static final Logger controller = LoggerFactory.getLogger("StopController");
    private static final Logger service    = LoggerFactory.getLogger("StopService");
    private static final Logger repository = LoggerFactory.getLogger("StopRepository");
    private static final Logger async      = LoggerFactory.getLogger("AsyncTask");

    public static void main(String[] args) throws Exception {

        // ──────────────────────────────────────────────────────────────────────
        // 시나리오 1: 인증된 사용자 요청
        //   GET /api/v1/stop/42  |  Authorization: Bearer <jwt>
        //   X-Request-ID: front-abc-123  (클라이언트가 전달한 ID)
        // ──────────────────────────────────────────────────────────────────────
        divider("시나리오 1 — 인증 사용자 요청 (X-Request-ID 헤더 있음)");

        simulateRequest("front-abc-123-def-456-789", "user-9f4e2a1b", () -> {

            // ↓ 개발자가 실제로 작성하는 코드 — Logger.info() 호출만 하면 된다
            controller.info("getStop() 호출 — stopId=42");
            service.info("StopService.findById() 실행");
            repository.debug("SELECT * FROM stop.stop WHERE id = ? 쿼리 실행");
            service.info("정류장 조회 완료 — name=강남역 버스정류소");
            controller.info("HTTP 200 OK 반환");

        });

        sleep();

        // ──────────────────────────────────────────────────────────────────────
        // 시나리오 2: 비인증 요청 (헬스체크, 공개 API 등)
        //   GET /api/v1/stop/health-check
        //   X-Request-ID 헤더 없음 → 필터가 UUID 자동 생성
        // ──────────────────────────────────────────────────────────────────────
        divider("시나리오 2 — 비인증 요청 (userId 없음, requestId 자동 생성)");

        simulateRequest(null /* userId 없음 */, null /* 헤더 없음 → UUID 자동생성 */, () -> {

            controller.info("healthCheck() 호출");
            controller.info("HTTP 200 OK 반환");

        });

        sleep();

        // ──────────────────────────────────────────────────────────────────────
        // 시나리오 3: 예외 발생
        //   POST /api/v1/reservation  — 이미 예약된 좌석 충돌
        //   예외 후에도 MDC가 정리되는지 확인
        // ──────────────────────────────────────────────────────────────────────
        divider("시나리오 3 — 예외 발생 → 요청 후 MDC 정리 확인");

        try {
            simulateRequest("req-xyz-error-001-abc-def", "user-error-case", () -> {

                service.info("ReservationService.reserve() 실행 — seatId=5A");
                repository.debug("SELECT ... FOR UPDATE 실행");
                service.warn("좌석 5A 이미 예약됨, 충돌 감지");
                throw new RuntimeException("SeatAlreadyReservedException: seat=5A");

            });
        } catch (RuntimeException e) {
            // ExceptionAdvisor가 잡아서 ErrorView 반환
        }

        // 필터의 finally 블록이 실행돼 MDC가 비어있어야 한다
        System.out.println();
        System.out.printf("  ✅ 예외 후 ThreadContext.requestId = \"%s\"  (null이어야 정상)%n", ThreadContext.get("requestId"));
        System.out.printf("  ✅ 예외 후 ThreadContext.userId    = \"%s\"  (null이어야 정상)%n%n", ThreadContext.get("userId"));

        sleep();

        // ──────────────────────────────────────────────────────────────────────
        // 시나리오 4: 비동기 스레드 — MDC 전파 문제와 해결책
        //   @Async, CompletableFuture.supplyAsync() 등 새 스레드를 만들면
        //   MDC가 자동으로 복사되지 않는다 (ThreadLocal 한계)
        // ──────────────────────────────────────────────────────────────────────
        divider("시나리오 4 — 비동기 스레드 MDC 전파");

        simulateRequest("req-async-demo-uuid-0001", "user-async", () -> {

            service.info("동기 로직 실행 — MDC 정상");

            // ❌ 잘못된 방법: 새 스레드에서 ThreadContext(MDC)가 비어있다
            System.out.printf("%n  ── ❌ 잘못된 방법 (MDC 복사 없음) ──%n%n");
            CompletableFuture.runAsync(() ->
                async.warn("새 스레드 — requestId가 비어있음: \"{}\"", ThreadContext.get("requestId"))
            ).join();

            // ✅ 올바른 방법: MDC 스냅샷을 명시적으로 복사해서 전달
            // 실제 코드에서는 MDC.getCopyOfContextMap() 사용 (Spring Boot 환경)
            System.out.printf("%n  ── ✅ 올바른 방법 (MDC 스냅샷 복사) ──%n%n");
            Map<String, String> mdcSnapshot = ThreadContext.getImmutableContext(); // ← 이 한 줄이 핵심

            CompletableFuture.runAsync(() -> {
                ThreadContext.putAll(mdcSnapshot);                       // 새 스레드에 복사
                try {
                    async.info("새 스레드 — requestId 정상 전파됨: {}", ThreadContext.get("requestId"));
                    async.info("외부 API 호출 완료");
                } finally {
                    ThreadContext.clearAll();                             // 새 스레드도 반드시 정리
                }
            }).join();

            service.info("비동기 작업 완료 후 동기 로직 재개 — MDC 여전히 유지");
        });

        sleep();

        // ──────────────────────────────────────────────────────────────────────
        // 시나리오 5: Virtual Thread 핵심 검증 3가지
        //
        //  [5-1] Park/Resume: VT가 carrier thread를 반납해도 MDC가 유지되는가?
        //  [5-2] 동시 VT 격리: 여러 VT가 동시에 실행될 때 requestId가 교차 오염되지 않는가?
        //  [5-3] 자식 VT 미상속: 새로 만든 VT에는 부모의 MDC가 없다 (+ 해결책)
        // ──────────────────────────────────────────────────────────────────────
        divider("시나리오 5 — Virtual Thread 핵심 검증");

        // ── [5-1] Park/Resume 중 MDC 보존 ─────────────────────────────────────
        System.out.printf("  [5-1] VT park/resume — carrier thread 반납 후에도 MDC 유지 확인%n%n");

        Thread.ofVirtual().name("vt-request-handler").start(() -> {
            ThreadContext.put("requestId", "vt-park-resume-req-001");
            ThreadContext.put("userId",    "vt-user-park");
            ThreadContext.put("traceId",   "parktrace00001a");

            service.info("VT 실행 — IO 호출 직전 (carrier thread 점유 중)");

            try { Thread.sleep(30); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            // ↑ sleep() → VT가 park되어 carrier thread 반납
            //   재개 시 다른 carrier thread에서 실행될 수 있다
            //   ThreadLocal은 carrier thread가 아닌 VT에 속하므로 MDC가 그대로다

            service.info("VT 재개 — carrier thread 재할당 후 (MDC 유지 ✅)");
            ThreadContext.clearAll();
        }).join();

        sleep();

        // ── [5-2] 동시 VT 격리 ────────────────────────────────────────────────
        System.out.printf("%n  [5-2] 동시 VT 격리 — 각 VT의 requestId가 독립적으로 유지되는지 확인%n");
        System.out.printf("        각 줄의 req= 값이 vt-N-req 형식으로 뒤섞이지 않아야 한다%n%n");

        int VT_COUNT = 6;
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch done      = new CountDownLatch(VT_COUNT);

        for (int i = 1; i <= VT_COUNT; i++) {
            final String reqId    = "vt-" + i + "-req-concurrent";
            final String traceId  = "conctrace0000" + i;
            Thread.ofVirtual().name("vt-concurrent-" + i).start(() -> {
                try {
                    startGate.await(); // 전원 동시 출발
                    ThreadContext.put("requestId", reqId);
                    ThreadContext.put("traceId",   traceId);

                    Thread.sleep((long)(Math.random() * 30)); // 불규칙 park

                    // 재개 후 자신의 requestId가 다른 VT 값으로 오염되지 않았어야 한다
                    service.info("동시 VT 재개 — 자신의 req= 값을 확인하세요: {}", ThreadContext.get("requestId"));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    ThreadContext.clearAll();
                    done.countDown();
                }
            });
        }
        startGate.countDown(); // 일제 출발
        done.await();

        sleep();

        // ── [5-3] 자식 VT MDC 미상속 + 해결책 ────────────────────────────────
        System.out.printf("%n  [5-3] 자식 VT — MDC 미상속 문제와 해결책%n%n");

        Thread.ofVirtual().name("vt-parent").start(() -> {
            ThreadContext.put("requestId", "vt-parent-req-9999");
            ThreadContext.put("traceId",   "parenttrace0001");

            service.info("부모 VT — 자식 VT 2개 생성 직전");

            // ❌ 자식 VT: ThreadLocal은 새 스레드로 전파되지 않는다
            System.out.printf("%n    ── ❌ 자식 VT (복사 없음) ──%n%n");
            Thread childNoMdc = Thread.ofVirtual().name("vt-child-no-mdc").start(() ->
                async.warn("자식 VT — requestId 비어있음: \"{}\" ❌", ThreadContext.get("requestId"))
            );
            try { childNoMdc.join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

            // ✅ MDC 스냅샷을 명시적으로 캡처한 뒤 자식 VT에 복원
            System.out.printf("%n    ── ✅ 자식 VT (MDC 스냅샷 복사) ──%n%n");
            Map<String, String> snapshot = ThreadContext.getImmutableContext();

            Thread childWithMdc = Thread.ofVirtual().name("vt-child-with-mdc").start(() -> {
                ThreadContext.putAll(snapshot);              // 스냅샷 복원
                try {
                    async.info("자식 VT — requestId 정상: \"{}\" ✅", ThreadContext.get("requestId"));
                } finally {
                    ThreadContext.clearAll();                // 자식 VT도 반드시 정리
                }
            });
            try { childWithMdc.join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

            service.info("부모 VT — 자식 완료 후 자신의 MDC 여전히 유지");
            ThreadContext.clearAll();
        }).join();

        sleep();

        // ──────────────────────────────────────────────────────────────────────
        // 실제 사용법 요약 출력
        // ──────────────────────────────────────────────────────────────────────
        divider("실제 코드에서 사용법");
        printUsageGuide();
    }

    // ── 내부 시뮬레이터 ───────────────────────────────────────────────────────

    /**
     * MdcLoggingFilter의 동작을 수동으로 재현한다.
     * 실제 HTTP 환경에서는 이 코드를 작성할 필요가 없다 — 필터가 자동으로 처리한다.
     *
     * <p><b>데모 주의:</b> 이 시뮬레이션 메서드는 Log4j2의 {@code ThreadContext}를 직접 사용한다.
     * 실제 {@link MdcLoggingFilter}는 {@code org.slf4j.MDC}를 사용하며,
     * Spring Boot 환경에서 {@code log4j-slf4j2-impl}이 이를 {@code ThreadContext}로 자동 위임한다.
     * non-Spring main()에서는 브릿지 초기화가 보장되지 않아 직접 호출한다.
     *
     * @param requestId X-Request-ID 헤더 값. null이면 UUID 자동 생성.
     * @param userId    인증된 사용자 ID. null이면 주입 생략.
     * @param logic     비즈니스 로직 (컨트롤러/서비스/레포지토리 호출)
     */
    private static void simulateRequest(String requestId, String userId, Runnable logic) {

        // ── 필터 진입 (MdcLoggingFilter.doFilterInternal 역할) ──────────────
        String resolvedId = (requestId != null && !requestId.isBlank())
                ? requestId
                : UUID.randomUUID().toString();

        // 실제 MdcLoggingFilter 코드: MDC.put("requestId", resolvedId)
        // Spring Boot 환경에서 org.slf4j.MDC → ThreadContext로 자동 브릿지된다
        ThreadContext.put("requestId", resolvedId);
        if (userId != null) {
            ThreadContext.put("userId", userId);
        }
        // Micrometer Tracing이 실제로는 여기서 traceId/spanId도 자동 주입한다.
        // 데모에서는 가짜 값으로 시뮬레이션한다.
        ThreadContext.put("traceId", UUID.randomUUID().toString().replace("-", "").substring(0, 16));

        filter.info("── 요청 진입 ── MDC 주입 완료: requestId={}, userId={}", resolvedId, userId);

        // ── 비즈니스 로직 실행 ───────────────────────────────────────────────
        try {
            logic.run();
        } finally {
            // ── 필터 종료 (finally 블록에서 반드시 실행) ────────────────────
            filter.info("── 요청 종료 ── MDC 정리");
            ThreadContext.remove("requestId");
            ThreadContext.remove("userId");
            ThreadContext.remove("traceId");
        }
    }

    private static void divider(String title) {
        System.out.printf("%n%n┌────────────────────────────────────────────────────────────────────────────┐%n");
        System.out.printf("│  %-74s│%n", title);
        System.out.printf("└────────────────────────────────────────────────────────────────────────────┘%n%n");
    }

    private static void sleep() throws InterruptedException {
        Thread.sleep(50); // 로그 출력 순서 안정화
    }

    private static void printUsageGuide() {
        System.out.println("""
                ┌─ 개발자가 작성하는 코드 ───────────────────────────────────────────────────────────┐
                │                                                                              │
                │  // 1. 각 클래스에 Logger 선언 (평소와 동일)                                        │
                │  private static final Logger log = LoggerFactory.getLogger(MyService.class); │
                │                                                                              │
                │  // 2. 로그 호출 (MDC 건드리지 않아도 requestId, userId가 자동으로 붙음)               │
                │  log.info("처리 시작 — itemId={}", itemId);                                    │
                │  log.debug("DB 쿼리 실행");                                                    │
                │  log.error("처리 실패", exception);                                            │
                │                                                                              │
                │  // 3. @Async / CompletableFuture 사용 시에만 MDC 수동 복사                      │
                │  Map<String, String> mdc = MDC.getCopyOfContextMap();                       │
                │  CompletableFuture.runAsync(() -> {                                          │
                │      MDC.setContextMap(mdc);                                                 │
                │      try { log.info("비동기 작업"); } finally { MDC.clear(); }             │
                │  });                                                                          │
                │                                                                              │
                │  ↑ 이것이 전부다. MDC.put(), requestId 생성 등은 모두 필터가 담당한다.     │
                │                                                                              │
                └──────────────────────────────────────────────────────────────────────────────┘

                ┌─ CloudWatch Logs Insights 쿼리 예시 ───────────────────────────────────────┐
                │                                                                              │
                │  # 특정 요청의 전체 로그 추적                                              │
                │  fields @timestamp, level, message, userId                                  │
                │  | filter requestId = "front-abc-123-def-456-789"                           │
                │  | sort @timestamp asc                                                       │
                │                                                                              │
                │  # 에러 로그에서 traceId로 분산 추적                                      │
                │  fields @timestamp, level, message, requestId, service                      │
                │  | filter level = "ERROR"                                                    │
                │  | filter traceId = "a1b2c3d4e5f60001"                                     │
                │                                                                              │
                └──────────────────────────────────────────────────────────────────────────────┘
                """);
    }
}
