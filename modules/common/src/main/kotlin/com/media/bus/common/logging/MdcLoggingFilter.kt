package com.media.bus.common.logging

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

/**
 * ## HTTP 요청 단위로 MDC(Mapped Diagnostic Context) 필드를 주입하는 필터
 *
 * 주입 필드:
 *   - `requestId` -- X-Request-ID 요청 헤더 값, 없으면 UUID v4 생성
 *   - `memberId` -- 인증된 사용자의 Principal name (비인증 요청은 생략)
 *
 * traceId / spanId 는 Micrometer Tracing(OTel 브릿지)이 자동으로 주입한다.
 *
 * 필터 순서(order=0): Spring Security(-100) 이후 실행되므로
 * SecurityContextHolder에서 인증 정보를 읽을 수 있다.
 *
 * **Virtual Thread 주의:** MDC는 ThreadLocal 기반이다.
 * `@Async` 등으로 새 스레드를 생성할 경우 `MDC.getCopyOfContextMap()`으로
 * 컨텍스트를 직접 복사해야 한다.
 */
class MdcLoggingFilter : OncePerRequestFilter() {

    companion object {
        private const val REQUEST_ID_HEADER = "X-Request-ID"
        private const val MDC_REQUEST_ID = "requestId"
        private const val MDC_USER_ID = "memberId"
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        try {
            val requestId = resolveRequestId(request)
            MDC.put(MDC_REQUEST_ID, requestId)
            response.setHeader(REQUEST_ID_HEADER, requestId)

            resolveMemberId(request)?.let { uid -> MDC.put(MDC_USER_ID, uid) }

            filterChain.doFilter(request, response)
        } finally {
            // traceId/spanId는 Micrometer Tracing이 관리하므로 제거하지 않는다
            MDC.remove(MDC_REQUEST_ID)
            MDC.remove(MDC_USER_ID)
        }
    }

    private fun resolveRequestId(request: HttpServletRequest): String {
        val requestId = request.getHeader(REQUEST_ID_HEADER)
        return if (!requestId.isNullOrBlank()) requestId else UUID.randomUUID().toString()
    }

    // Gateway가 주입한 X-User-Id 헤더에서 memberId를 읽는다.
    // SecurityContextHolder(ThreadLocal 기반) 대신 request 객체를 직접 사용하여
    // Virtual Thread 환경에서도 안전하게 동작한다.
    private fun resolveMemberId(request: HttpServletRequest): String? {
        val userId = request.getHeader("X-User-Id")
        return if (!userId.isNullOrBlank()) userId else null
    }
}
