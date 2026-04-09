package com.media.bus.common.logging

import io.mockk.every
import io.mockk.mockk
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.*
import org.slf4j.MDC
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import java.util.concurrent.atomic.AtomicReference

@DisplayName("MdcLoggingFilter")
class MdcLoggingFilterTest {

    private val filterChain = mockk<FilterChain>(relaxed = true)
    private val filter = MdcLoggingFilter()

    @BeforeEach
    fun setUp() {
        SecurityContextHolder.clearContext()
        MDC.clear()
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
        MDC.clear()
    }

    // ── X-Request-ID 처리 ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("X-Request-ID 처리")
    inner class RequestIdTest {

        @Test
        @DisplayName("X-Request-ID 헤더가 있으면 MDC에 해당 값을 주입한다")
        fun shouldUsePredefinedRequestId() {
            val request = MockHttpServletRequest()
            request.addHeader("X-Request-ID", "my-predefined-request-id")
            val captured = captureFromMdc("requestId")

            filter.doFilter(request, MockHttpServletResponse(), filterChain)

            assertThat(captured.get()).isEqualTo("my-predefined-request-id")
        }

        @Test
        @DisplayName("X-Request-ID 헤더가 없으면 UUID를 생성하여 MDC에 주입한다")
        fun shouldGenerateUuidWhenNoHeader() {
            val captured = captureFromMdc("requestId")

            filter.doFilter(MockHttpServletRequest(), MockHttpServletResponse(), filterChain)

            assertThat(captured.get())
                .isNotNull()
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        }

        @Test
        @DisplayName("공백 X-Request-ID 헤더는 무시하고 UUID를 생성한다")
        fun shouldGenerateUuidForBlankHeader() {
            val request = MockHttpServletRequest()
            request.addHeader("X-Request-ID", "   ")
            val captured = captureFromMdc("requestId")

            filter.doFilter(request, MockHttpServletResponse(), filterChain)

            assertThat(captured.get())
                .isNotBlank()
                .doesNotContainOnlyWhitespaces()
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        }

        @Test
        @DisplayName("응답 헤더 X-Request-ID에 requestId를 반환한다")
        fun shouldSetResponseHeader() {
            val request = MockHttpServletRequest()
            request.addHeader("X-Request-ID", "trace-001")
            val response = MockHttpServletResponse()

            filter.doFilter(request, response, filterChain)

            assertThat(response.getHeader("X-Request-ID")).isEqualTo("trace-001")
        }

        @Test
        @DisplayName("헤더 없을 때 응답 X-Request-ID는 자동 생성된 UUID다")
        fun shouldSetGeneratedIdInResponseHeader() {
            val response = MockHttpServletResponse()

            filter.doFilter(MockHttpServletRequest(), response, filterChain)

            assertThat(response.getHeader("X-Request-ID"))
                .isNotNull()
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        }

        @Test
        @DisplayName("요청과 응답의 X-Request-ID가 동일한 값이다")
        fun requestAndResponseHeaderShouldMatch() {
            val request = MockHttpServletRequest()
            val response = MockHttpServletResponse()
            val capturedMdc = captureFromMdc("requestId")

            filter.doFilter(request, response, filterChain)

            assertThat(response.getHeader("X-Request-ID")).isEqualTo(capturedMdc.get())
        }
    }

    // ── memberId 처리 ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("memberId 처리")
    inner class MemberIdTest {

        @Test
        @DisplayName("X-User-Id 헤더가 있으면 memberId를 MDC에 주입한다")
        fun shouldInjectMemberIdForAuthenticatedUser() {
            val request = MockHttpServletRequest()
            request.addHeader("X-User-Id", "user-123")
            val captured = captureFromMdc("memberId")

            filter.doFilter(request, MockHttpServletResponse(), filterChain)

            assertThat(captured.get()).isEqualTo("user-123")
        }

        @Test
        @DisplayName("anonymousUser일 때 memberId를 MDC에 주입하지 않는다")
        fun shouldNotInjectMemberIdForAnonymous() {
            val anonymousAuth = UsernamePasswordAuthenticationToken(
                "anonymousUser", null, listOf(SimpleGrantedAuthority("ROLE_ANONYMOUS"))
            )
            SecurityContextHolder.getContext().authentication = anonymousAuth
            val captured = captureFromMdc("memberId")

            filter.doFilter(MockHttpServletRequest(), MockHttpServletResponse(), filterChain)

            assertThat(captured.get()).isNull()
        }

        @Test
        @DisplayName("SecurityContext가 비어있으면 memberId를 MDC에 주입하지 않는다")
        fun shouldNotInjectMemberIdWhenNoAuthentication() {
            val captured = captureFromMdc("memberId")

            filter.doFilter(MockHttpServletRequest(), MockHttpServletResponse(), filterChain)

            assertThat(captured.get()).isNull()
        }

        @Test
        @DisplayName("인증 객체가 있어도 isAuthenticated()가 false면 주입하지 않는다")
        fun shouldNotInjectMemberIdWhenNotAuthenticated() {
            val unauthenticated = UsernamePasswordAuthenticationToken.unauthenticated("some-user", null)
            SecurityContextHolder.getContext().authentication = unauthenticated
            val captured = captureFromMdc("memberId")

            filter.doFilter(MockHttpServletRequest(), MockHttpServletResponse(), filterChain)

            assertThat(captured.get()).isNull()
        }
    }

    // ── MDC 생명주기 ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("MDC 생명주기")
    inner class MdcLifecycleTest {

        @Test
        @DisplayName("필터 체인 실행 중 requestId와 memberId가 MDC에 존재한다")
        fun shouldHaveMdcFieldsDuringFilterChainExecution() {
            val request = MockHttpServletRequest()
            request.addHeader("X-Request-ID", "trace-001")
            request.addHeader("X-User-Id", "user-abc")
            val capturedRequestId = AtomicReference<String>()
            val capturedMemberId = AtomicReference<String>()
            every { filterChain.doFilter(any(), any()) } answers {
                capturedRequestId.set(MDC.get("requestId"))
                capturedMemberId.set(MDC.get("memberId"))
            }

            filter.doFilter(request, MockHttpServletResponse(), filterChain)

            assertThat(capturedRequestId.get()).isEqualTo("trace-001")
            assertThat(capturedMemberId.get()).isEqualTo("user-abc")
        }

        @Test
        @DisplayName("요청 완료 후 requestId가 MDC에서 제거된다")
        fun shouldRemoveRequestIdAfterRequest() {
            val request = MockHttpServletRequest()
            request.addHeader("X-Request-ID", "trace-001")

            filter.doFilter(request, MockHttpServletResponse(), filterChain)

            assertThat(MDC.get("requestId")).isNull()
        }

        @Test
        @DisplayName("요청 완료 후 memberId가 MDC에서 제거된다")
        fun shouldRemoveMemberIdAfterRequest() {
            authenticateAs("user-abc")

            filter.doFilter(MockHttpServletRequest(), MockHttpServletResponse(), filterChain)

            assertThat(MDC.get("memberId")).isNull()
        }

        @Test
        @DisplayName("필터 체인에서 ServletException이 발생해도 MDC가 정리된다")
        fun shouldClearMdcEvenOnServletException() {
            val request = MockHttpServletRequest()
            request.addHeader("X-Request-ID", "error-request")
            authenticateAs("user-xyz")
            every { filterChain.doFilter(any(), any()) } throws ServletException("downstream error")

            assertThatThrownBy { filter.doFilter(request, MockHttpServletResponse(), filterChain) }
                .isInstanceOf(ServletException::class.java)
                .hasMessage("downstream error")

            assertThat(MDC.get("requestId")).isNull()
            assertThat(MDC.get("memberId")).isNull()
        }

        @Test
        @DisplayName("필터 체인에서 RuntimeException이 발생해도 MDC가 정리된다")
        fun shouldClearMdcEvenOnRuntimeException() {
            authenticateAs("user-xyz")
            every { filterChain.doFilter(any(), any()) } throws RuntimeException("unexpected")

            assertThatThrownBy { filter.doFilter(MockHttpServletRequest(), MockHttpServletResponse(), filterChain) }
                .isInstanceOf(RuntimeException::class.java)

            assertThat(MDC.get("requestId")).isNull()
            assertThat(MDC.get("memberId")).isNull()
        }

        @Test
        @DisplayName("연속 요청 간 MDC 값이 격리된다 — 이전 요청의 requestId가 남지 않는다")
        fun shouldIsolateMdcBetweenRequests() {
            val firstCapture = AtomicReference<String>()
            every { filterChain.doFilter(any(), any()) } answers { firstCapture.set(MDC.get("requestId")) }
            val firstRequest = MockHttpServletRequest()
            firstRequest.addHeader("X-Request-ID", "req-1")
            filter.doFilter(firstRequest, MockHttpServletResponse(), filterChain)

            val secondCapture = AtomicReference<String>()
            every { filterChain.doFilter(any(), any()) } answers { secondCapture.set(MDC.get("requestId")) }
            val secondRequest = MockHttpServletRequest()
            secondRequest.addHeader("X-Request-ID", "req-2")
            filter.doFilter(secondRequest, MockHttpServletResponse(), filterChain)

            assertThat(firstCapture.get()).isEqualTo("req-1")
            assertThat(secondCapture.get()).isEqualTo("req-2")
        }

        @Test
        @DisplayName("Micrometer Tracing이 주입한 traceId/spanId는 필터가 제거하지 않는다")
        fun shouldNotRemoveMicrometerTracingFields() {
            MDC.put("traceId", "otel-trace-id-123")
            MDC.put("spanId", "otel-span-id-456")

            filter.doFilter(MockHttpServletRequest(), MockHttpServletResponse(), filterChain)

            assertThat(MDC.get("traceId")).isEqualTo("otel-trace-id-123")
            assertThat(MDC.get("spanId")).isEqualTo("otel-span-id-456")
        }
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────────────────

    /** filterChain.doFilter() 실행 시점의 MDC 값을 캡처하는 AtomicReference를 반환한다. */
    private fun captureFromMdc(key: String): AtomicReference<String?> {
        val ref = AtomicReference<String?>()
        every { filterChain.doFilter(any(), any()) } answers { ref.set(MDC.get(key)) }
        return ref
    }

    private fun authenticateAs(username: String) {
        val auth = UsernamePasswordAuthenticationToken(
            username, null, listOf(SimpleGrantedAuthority("ROLE_USER"))
        )
        SecurityContextHolder.getContext().authentication = auth
    }
}
