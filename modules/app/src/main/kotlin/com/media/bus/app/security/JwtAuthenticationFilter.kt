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

/**
 * ## 공개 포트 인증 필터 (예전 gateway `JwtAuthenticationFilter`의 서블릿판)
 *
 * Spring Security 필터 체인 **안에** 둔다(빈으로 등록하지 않는다 — 서블릿 필터로 한 번 더 등록되는 것을 막기 위해).
 * 체인 앞단의 `StrictHttpFirewall`이 이상한 경로를 먼저 거절해야, 여기서 본 경로와 핸들러 매핑이 해석한 경로가 같다.
 *
 * 처리 순서:
 * 1. 모든 요청에서 신뢰 헤더(`X-User-*` 등 6개)를 숨긴다([TrustedHeaderStrippingRequest]).
 * 2. 라우트 허용 목록 밖이면 404(본문 없음).
 * 3. 공개 엔드포인트면 토큰 없이 통과.
 * 4. `Authorization: Bearer` 토큰을 검증한다. 없거나 잘못됐거나 액세스 토큰이 아니면 401(본문 없음, gateway와 같다).
 * 5. `/api/v1/admin` 하위은 관리자 사이트 토큰(`aud=ADMIN`) + 관리자 회원 유형만 통과. 아니면 403(본문 없음).
 * 6. 검증한 `MemberPrincipal`을 request attribute로 넣는다. `@CurrentMember`·`@Authorize`가 여기서 읽는다.
 *
 * 관리 포트(Actuator)로 들어온 요청은 건너뛴다. 관리 포트는 루프백에만 열려 있다.
 * `request.localPort`는 요청을 받은 커넥터 포트라 헤더처럼 위조할 수 없다.
 *
 * @param managementPort 관리 포트를 돌려주는 함수. `null`이면 관리 포트가 따로 없다(모든 요청을 검사).
 *   설정이 `0`(임의 포트)이면 관리 서버가 뜬 뒤에야 실제 포트를 알 수 있어서 값이 아니라 함수로 받는다.
 */
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
        // 넣기만 한다. 지우는 것은 가장 바깥 MdcLoggingFilter 의 finally 가 맡는다 — 여기서 지우면 ACCESS 로그에 회원 ID가 빠진다.
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
        // 액세스 토큰에는 type 클레임이 없다. refresh 토큰(type=refresh)을 액세스 토큰으로 쓰지 못하게 막는다.
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

        // 발급처 경계: 관리자 API 는 관리자 계정 + 관리자 사이트 토큰만. 컨트롤러 @Authorize 와 별개의 2중 방어다.
        // memberType 은 발급 시점 클레임이라 해임된 관리자는 액세스 토큰 TTL(15분) 동안 통과할 수 있다(gateway 와 같다).
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
