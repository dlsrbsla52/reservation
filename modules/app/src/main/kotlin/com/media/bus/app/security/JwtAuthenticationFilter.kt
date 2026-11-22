package com.media.bus.app.security

import com.media.bus.common.logging.MdcLoggingFilter
import com.media.bus.contract.security.JwtProvider
import com.media.bus.contract.security.MemberPrincipal
import com.media.bus.contract.security.TokenAudience
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.http.HttpHeaders
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.UrlPathHelper

/** 공개 포트 인증 필터 (예전 gateway `JwtAuthenticationFilter`의 서블릿판) */
class JwtAuthenticationFilter(
    private val jwtProvider: JwtProvider,
    private val exposeApiDocs: Boolean,
    private val managementPort: () -> Int?,
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(javaClass)
    private val pathHelper = UrlPathHelper()

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        managementPort()?.let { request.localPort == it } ?: false

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val sanitized = TrustedHeaderStrippingRequest(request)
        val method = request.method
        val path = pathHelper.getPathWithinApplication(request)

        if (!ApiPathPolicy.isRoutable(path, exposeApiDocs)) {
            response.status = HttpServletResponse.SC_NOT_FOUND
            return
        }
        if (ApiPathPolicy.isPublic(method, path) || ApiPathPolicy.isApiDoc(path)) {
            chain.doFilter(sanitized, response)
            return
        }

        val principal = when (val result = authenticate(request, path)) {
            AuthResult.Unauthorized -> {
                response.status = HttpServletResponse.SC_UNAUTHORIZED
                return
            }
            AuthResult.Forbidden -> {
                response.status = HttpServletResponse.SC_FORBIDDEN
                return
            }
            is AuthResult.Authenticated -> result.principal
        }

        sanitized.setAttribute(MemberPrincipal.REQUEST_ATTRIBUTE_KEY, principal)
        MDC.put(MdcLoggingFilter.MDC_MEMBER_ID, principal.id.toString())
        chain.doFilter(sanitized, response)
    }

    private fun authenticate(request: HttpServletRequest, path: String): AuthResult {
        val header = request.getHeader(HttpHeaders.AUTHORIZATION)
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            log.warn("[JwtAuthenticationFilter] Authorization 헤더 없음. path={}", path)
            return AuthResult.Unauthorized
        }
        val claims = jwtProvider.tryParseClaims(header.substring(BEARER_PREFIX.length))
        if (claims == null) {
            log.warn("[JwtAuthenticationFilter] 유효하지 않은 토큰. path={}", path)
            return AuthResult.Unauthorized
        }
        if (claims[TOKEN_TYPE_CLAIM] != null) {
            log.warn("[JwtAuthenticationFilter] 액세스 토큰이 아님. type={}, path={}", claims[TOKEN_TYPE_CLAIM], path)
            return AuthResult.Unauthorized
        }
        val principal = try {
            jwtProvider.getPrincipalFromClaims(claims)
        } catch (e: Exception) {
            log.warn("[JwtAuthenticationFilter] 클레임 파싱 실패. path={}, error={}", path, e.message)
            return AuthResult.Unauthorized
        }

        if (ApiPathPolicy.isAdmin(path)) {
            val audience = TokenAudience.fromClaims(claims)
            if (audience != TokenAudience.ADMIN || !principal.memberType.isAdmin) {
                log.warn(
                    "[JwtAuthenticationFilter] 발급처 불일치로 차단. memberId={}, memberType={}, path={}",
                    principal.id, principal.memberType.name, path,
                )
                return AuthResult.Forbidden
            }
        }
        return AuthResult.Authenticated(principal)
    }

    /** 인증 결과: 401 / 403 / 통과. */
    private sealed interface AuthResult {
        data object Unauthorized : AuthResult
        data object Forbidden : AuthResult
        data class Authenticated(val principal: MemberPrincipal) : AuthResult
    }

    private companion object {
        const val BEARER_PREFIX = "Bearer "
        const val TOKEN_TYPE_CLAIM = "type"
    }
}
