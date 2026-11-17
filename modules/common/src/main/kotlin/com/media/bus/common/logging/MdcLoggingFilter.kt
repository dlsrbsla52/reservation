package com.media.bus.common.logging

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.web.filter.OncePerRequestFilter
import java.util.*

/**
 * ## HTTP 요청 단위로 MDC(Mapped Diagnostic Context) 필드를 주입하는 필터
 *
 * 주입 필드:
 *   - `requestId` -- X-Request-ID 요청 헤더 값, 없으면 UUID v4 생성
 *   - `memberId` -- 이 필터는 넣지 않는다. 인증 필터가 토큰 검증에 성공한 뒤 [MDC_MEMBER_ID]로 넣고,
 *     이 필터(가장 바깥)의 `finally`가 지운다. 그래야 `AccessLoggingFilter`의 `ACCESS` 로그에도 회원 ID가 남는다.
 *
 * 예전에는 gateway가 넣어 준 `X-User-Id` 헤더를 그대로 MDC에 넣었다. 이 필터는 Spring Security보다 먼저 실행되므로,
 * 게이트웨이가 없어진 뒤에도 헤더를 읽으면 클라이언트가 위조한 회원 ID가 로그에 남는다.
 *
 * **Virtual Thread 주의:** MDC는 ThreadLocal 기반이다.
 * `@Async` 등으로 새 스레드를 생성할 경우 `MDC.getCopyOfContextMap()`으로
 * 컨텍스트를 직접 복사해야 한다.
 */
class MdcLoggingFilter : OncePerRequestFilter() {

    companion object {
        private const val REQUEST_ID_HEADER = "X-Request-ID"
        private const val MDC_REQUEST_ID = "requestId"
        /** 인증 필터가 검증한 회원 ID를 넣는 MDC 키. */
        const val MDC_MEMBER_ID = "memberId"
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

            filterChain.doFilter(request, response)
        } finally {
            MDC.remove(MDC_REQUEST_ID)
            MDC.remove(MDC_MEMBER_ID)
        }
    }

    private fun resolveRequestId(request: HttpServletRequest): String {
        val requestId = request.getHeader(REQUEST_ID_HEADER)
        return if (!requestId.isNullOrBlank()) requestId else UUID.randomUUID().toString()
    }
}
