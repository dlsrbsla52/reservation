package com.media.bus.contract.security.filter

import com.media.bus.contract.security.MemberPrincipal
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.web.filter.OncePerRequestFilter

/**
 * ## Gateway가 주입한 X-User-* 헤더에서 MemberPrincipal을 복원하여
 * HttpServletRequest attribute에 저장하는 서블릿 필터
 *
 * 처리 흐름:
 * 1. X-User-Id, X-User-Role 헤더 존재 여부 확인
 * 2. 헤더 누락 시 attribute 미설정(인터셉터에서 401 처리) 후 체인 통과
 * 3. MemberPrincipal.fromHeaders() 호출, 파싱 성공 시 request attribute 저장
 * 4. 파싱 실패(잘못된 UUID, 알 수 없는 MemberType 등) 시 warn 로그 후 attribute 미설정
 *
 * 설계 의도:
 * HttpServletRequest.setAttribute()는 요청 객체에 직접 저장이므로
 * ThreadLocal 없이 Virtual Thread 안전합니다.
 * JWT 재파싱 없이 Gateway가 검증/주입한 헤더를 신뢰하여 이중 검증을 제거합니다.
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 300)
class MemberPrincipalExtractFilter : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val memberId = request.getHeader(MemberPrincipal.HEADER_USER_ID)
        val loginId = request.getHeader(MemberPrincipal.HEADER_USER_LOGIN_ID)
        val email = request.getHeader(MemberPrincipal.HEADER_USER_EMAIL)
        val role = request.getHeader(MemberPrincipal.HEADER_USER_ROLE)
        val emailVerified = request.getHeader(MemberPrincipal.HEADER_EMAIL_VERIFIED)
        val permissionsHeader = request.getHeader(MemberPrincipal.HEADER_USER_PERMISSIONS)

        // 필수 헤더 누락 시 attribute 미설정 — 인터셉터가 @Authorize 여부에 따라 401 처리
        if (memberId == null || role == null) {
            filterChain.doFilter(request, response)
            return
        }

        try {
            val principal = MemberPrincipal.fromHeaders(
                memberId, loginId, email, role, emailVerified, permissionsHeader,
            )
            request.setAttribute(MemberPrincipal.REQUEST_ATTRIBUTE_KEY, principal)
        } catch (e: Exception) {
            // 헤더 값이 잘못된 경우(UUID 형식 오류, 알 수 없는 MemberType 등) warn 후 미설정
            log.warn("[MemberPrincipalExtractFilter] X-User-* 헤더 파싱 실패: {}", e.message)
        }

        filterChain.doFilter(request, response)
    }
}
