package com.media.bus.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
@DisplayName("MdcLoggingFilter")
class MdcLoggingFilterTest {

    @Mock
    private FilterChain filterChain;

    private final MdcLoggingFilter filter = new MdcLoggingFilter();

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    // ── X-Request-ID 처리 ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("X-Request-ID 처리")
    class RequestIdTest {

        @Test
        @DisplayName("X-Request-ID 헤더가 있으면 MDC에 해당 값을 주입한다")
        void shouldUsePredefinedRequestId() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-Request-ID", "my-predefined-request-id");
            AtomicReference<String> captured = captureFromMdc("requestId");

            filter.doFilter(request, new MockHttpServletResponse(), filterChain);

            assertThat(captured.get()).isEqualTo("my-predefined-request-id");
        }

        @Test
        @DisplayName("X-Request-ID 헤더가 없으면 UUID를 생성하여 MDC에 주입한다")
        void shouldGenerateUuidWhenNoHeader() throws Exception {
            AtomicReference<String> captured = captureFromMdc("requestId");

            filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), filterChain);

            assertThat(captured.get())
                    .isNotNull()
                    .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        }

        @Test
        @DisplayName("공백 X-Request-ID 헤더는 무시하고 UUID를 생성한다")
        void shouldGenerateUuidForBlankHeader() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-Request-ID", "   ");
            AtomicReference<String> captured = captureFromMdc("requestId");

            filter.doFilter(request, new MockHttpServletResponse(), filterChain);

            assertThat(captured.get())
                    .isNotBlank()
                    .doesNotContainOnlyWhitespaces()
                    .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        }

        @Test
        @DisplayName("응답 헤더 X-Request-ID에 requestId를 반환한다")
        void shouldSetResponseHeader() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-Request-ID", "trace-001");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, filterChain);

            assertThat(response.getHeader("X-Request-ID")).isEqualTo("trace-001");
        }

        @Test
        @DisplayName("헤더 없을 때 응답 X-Request-ID는 자동 생성된 UUID다")
        void shouldSetGeneratedIdInResponseHeader() throws Exception {
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(new MockHttpServletRequest(), response, filterChain);

            assertThat(response.getHeader("X-Request-ID"))
                    .isNotNull()
                    .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        }

        @Test
        @DisplayName("요청과 응답의 X-Request-ID가 동일한 값이다")
        void requestAndResponseHeaderShouldMatch() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();
            AtomicReference<String> capturedMdc = captureFromMdc("requestId");

            filter.doFilter(request, response, filterChain);

            assertThat(response.getHeader("X-Request-ID")).isEqualTo(capturedMdc.get());
        }
    }

    // ── userId 처리 ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("userId 처리")
    class UserIdTest {

        @Test
        @DisplayName("인증된 사용자가 있으면 Principal name을 MDC에 주입한다")
        void shouldInjectUserIdForAuthenticatedUser() throws Exception {
            authenticateAs("user-123");
            AtomicReference<String> captured = captureFromMdc("userId");

            filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), filterChain);

            assertThat(captured.get()).isEqualTo("user-123");
        }

        @Test
        @DisplayName("anonymousUser일 때 userId를 MDC에 주입하지 않는다")
        void shouldNotInjectUserIdForAnonymous() throws Exception {
            // Spring Security 기본 익명 사용자 principal = "anonymousUser" (문자열)
            UsernamePasswordAuthenticationToken anonymousAuth = new UsernamePasswordAuthenticationToken(
                    "anonymousUser", null, List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
            SecurityContextHolder.getContext().setAuthentication(anonymousAuth);
            AtomicReference<String> captured = captureFromMdc("userId");

            filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), filterChain);

            assertThat(captured.get()).isNull();
        }

        @Test
        @DisplayName("SecurityContext가 비어있으면 userId를 MDC에 주입하지 않는다")
        void shouldNotInjectUserIdWhenNoAuthentication() throws Exception {
            // SecurityContextHolder는 setUp()에서 이미 clear된 상태
            AtomicReference<String> captured = captureFromMdc("userId");

            filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), filterChain);

            assertThat(captured.get()).isNull();
        }

        @Test
        @DisplayName("인증 객체가 있어도 isAuthenticated()가 false면 주입하지 않는다")
        void shouldNotInjectUserIdWhenNotAuthenticated() throws Exception {
            // 자격증명 없이 생성한 토큰은 authenticated=false 가 될 수 있다
            // AbstractAuthenticationToken.setAuthenticated(false)로 명시 설정
            UsernamePasswordAuthenticationToken unauthenticated =
                    UsernamePasswordAuthenticationToken.unauthenticated("some-user", null);
            SecurityContextHolder.getContext().setAuthentication(unauthenticated);
            AtomicReference<String> captured = captureFromMdc("userId");

            filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), filterChain);

            assertThat(captured.get()).isNull();
        }
    }

    // ── MDC 생명주기 ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("MDC 생명주기")
    class MdcLifecycleTest {

        @Test
        @DisplayName("필터 체인 실행 중 requestId와 userId가 MDC에 존재한다")
        void shouldHaveMdcFieldsDuringFilterChainExecution() throws Exception {
            authenticateAs("user-abc");
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-Request-ID", "trace-001");
            AtomicReference<String> capturedRequestId = new AtomicReference<>();
            AtomicReference<String> capturedUserId = new AtomicReference<>();
            doAnswer(inv -> {
                capturedRequestId.set(MDC.get("requestId"));
                capturedUserId.set(MDC.get("userId"));
                return null;
            }).when(filterChain).doFilter(any(), any());

            filter.doFilter(request, new MockHttpServletResponse(), filterChain);

            assertThat(capturedRequestId.get()).isEqualTo("trace-001");
            assertThat(capturedUserId.get()).isEqualTo("user-abc");
        }

        @Test
        @DisplayName("요청 완료 후 requestId가 MDC에서 제거된다")
        void shouldRemoveRequestIdAfterRequest() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-Request-ID", "trace-001");

            filter.doFilter(request, new MockHttpServletResponse(), filterChain);

            assertThat(MDC.get("requestId")).isNull();
        }

        @Test
        @DisplayName("요청 완료 후 userId가 MDC에서 제거된다")
        void shouldRemoveUserIdAfterRequest() throws Exception {
            authenticateAs("user-abc");

            filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), filterChain);

            assertThat(MDC.get("userId")).isNull();
        }

        @Test
        @DisplayName("필터 체인에서 ServletException이 발생해도 MDC가 정리된다")
        void shouldClearMdcEvenOnServletException() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-Request-ID", "error-request");
            authenticateAs("user-xyz");
            doThrow(new ServletException("downstream error"))
                    .when(filterChain).doFilter(any(), any());

            assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(), filterChain))
                    .isInstanceOf(ServletException.class)
                    .hasMessage("downstream error");

            assertThat(MDC.get("requestId")).isNull();
            assertThat(MDC.get("userId")).isNull();
        }

        @Test
        @DisplayName("필터 체인에서 RuntimeException이 발생해도 MDC가 정리된다")
        void shouldClearMdcEvenOnRuntimeException() throws Exception {
            authenticateAs("user-xyz");
            doThrow(new RuntimeException("unexpected"))
                    .when(filterChain).doFilter(any(), any());

            assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), filterChain))
                    .isInstanceOf(RuntimeException.class);

            assertThat(MDC.get("requestId")).isNull();
            assertThat(MDC.get("userId")).isNull();
        }

        @Test
        @DisplayName("연속 요청 간 MDC 값이 격리된다 — 이전 요청의 requestId가 남지 않는다")
        void shouldIsolateMdcBetweenRequests() throws Exception {
            AtomicReference<String> firstCapture = new AtomicReference<>();
            doAnswer(inv -> { firstCapture.set(MDC.get("requestId")); return null; })
                    .when(filterChain).doFilter(any(), any());
            MockHttpServletRequest firstRequest = new MockHttpServletRequest();
            firstRequest.addHeader("X-Request-ID", "req-1");
            filter.doFilter(firstRequest, new MockHttpServletResponse(), filterChain);

            AtomicReference<String> secondCapture = new AtomicReference<>();
            doAnswer(inv -> { secondCapture.set(MDC.get("requestId")); return null; })
                    .when(filterChain).doFilter(any(), any());
            MockHttpServletRequest secondRequest = new MockHttpServletRequest();
            secondRequest.addHeader("X-Request-ID", "req-2");
            filter.doFilter(secondRequest, new MockHttpServletResponse(), filterChain);

            assertThat(firstCapture.get()).isEqualTo("req-1");
            assertThat(secondCapture.get()).isEqualTo("req-2");
        }

        @Test
        @DisplayName("Micrometer Tracing이 주입한 traceId/spanId는 필터가 제거하지 않는다")
        void shouldNotRemoveMicrometerTracingFields() throws Exception {
            // Micrometer Tracing이 MDC에 이미 주입했다고 가정
            MDC.put("traceId", "otel-trace-id-123");
            MDC.put("spanId", "otel-span-id-456");

            filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), filterChain);

            // MdcLoggingFilter는 traceId/spanId를 제거하지 않는다 (Micrometer 관리 필드)
            assertThat(MDC.get("traceId")).isEqualTo("otel-trace-id-123");
            assertThat(MDC.get("spanId")).isEqualTo("otel-span-id-456");
        }
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────────────────

    /**
     * filterChain.doFilter() 실행 시점의 MDC 값을 캡처하는 AtomicReference를 반환한다.
     * doAnswer stub은 이 메서드 호출 즉시 등록된다.
     */
    private AtomicReference<String> captureFromMdc(String key) throws Exception {
        AtomicReference<String> ref = new AtomicReference<>();
        doAnswer(inv -> { ref.set(MDC.get(key)); return null; })
                .when(filterChain).doFilter(any(), any());
        return ref;
    }

    private void authenticateAs(String username) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                username, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
