package com.media.bus.gateway.filter

import com.media.bus.contract.security.JwtProvider
import com.media.bus.contract.security.MemberPrincipal
import org.slf4j.LoggerFactory
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.filter.GlobalFilter
import org.springframework.core.Ordered
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

/// Gateway Edge Authentication Filter.
/// 모든 인입 요청에 대해 JWT Bearer 토큰 검증을 수행하는 Global Filter입니다.
/// 처리 흐름:
/// 1. 요청 경로가 Public Whitelist에 포함된 경우: 토큰 검증 없이 통과.
/// 2. Authorization 헤더에서 Bearer 토큰을 추출.
/// 3. JwtProvider로 서명 및 만료 검증.
/// 4. 검증 성공 시: 클레임 추출 → X-User-\* 헤더 주입 → 하위 서비스로 라우팅.
/// 5. 검증 실패 시: 401 Unauthorized 즉시 응답 (하위 서비스에 요청 도달 차단).
/// Gateway는 Reactive(WebFlux) 기반이므로 GlobalFilter를 Reactor 방식으로 구현합니다.
@Component
class JwtAuthenticationFilter(
    private val jwtProvider: JwtProvider,
) : GlobalFilter, Ordered {

    private val log = LoggerFactory.getLogger(JwtAuthenticationFilter::class.java)

    companion object {
        /// JWT 검증을 건너뛰는 Public Endpoint 목록.
        /// HTTP Method + Path 쌍으로 정의합니다.
        private val PUBLIC_PATHS = listOf(
            "POST:/api/v1/auth/register",
            "POST:/api/v1/auth/login",
            "POST:/api/v1/auth/token/refresh",
            "GET:/api/v1/auth/verify-email",
            "GET:/api/v1/auth/health-check",
            "GET:/api/v1/member/health-check",
            "GET:/api/v1/reservation/health-check",
            "GET:/api/v1/stop/health-check",
        )
    }

    override fun filter(exchange: ServerWebExchange, chain: GatewayFilterChain): Mono<Void> {
        val method = exchange.request.method.name()
        val path = exchange.request.uri.path

        // Public Endpoint는 인증 없이 통과
        if (isPublicPath(method, path)) {
            return chain.filter(exchange)
        }

        // Authorization 헤더에서 Bearer 토큰 추출
        val authHeader = exchange.request.headers.getFirst(HttpHeaders.AUTHORIZATION)
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("[JwtAuthenticationFilter] Authorization 헤더 없음. path={}", path)
            return unauthorized(exchange)
        }

        val token = authHeader.substring(7)

        // JWT 토큰 검증 + 클레임 파싱을 단일 호출로 통합하여 HMAC 연산 이중 수행 방지
        val claims = jwtProvider.tryParseClaims(token)
        if (claims == null) {
            log.warn("[JwtAuthenticationFilter] 유효하지 않은 토큰. path={}", path)
            return unauthorized(exchange)
        }

        // 클레임 추출 및 하위 서비스로 전달할 헤더 주입
        return try {
            val principal: MemberPrincipal = jwtProvider.getPrincipalFromClaims(claims)

            // 하위 서비스는 이 헤더를 신뢰하여 별도 JWT 파싱 없이 사용자 정보를 활용합니다.
            // loginId, email 헤더를 추가하여 MemberPrincipal.fromHeaders() 복원에 필요한 모든 정보를 전달합니다.
            // JWT claim에서 파싱된 permissions를 쉼표 구분 문자열로 변환하여 헤더에 주입
            val permissionsHeader = principal.permissions
                .joinToString(",") { it.name }

            val mutatedExchange = exchange.mutate()
                .request { r ->
                    r
                        .header(MemberPrincipal.HEADER_USER_ID,          principal.id.toString())
                        .header(MemberPrincipal.HEADER_USER_LOGIN_ID,    principal.loginId.orEmpty())
                        .header(MemberPrincipal.HEADER_USER_EMAIL,       principal.email.orEmpty())
                        .header(MemberPrincipal.HEADER_USER_ROLE,        "ROLE_" + principal.memberType.name)
                        .header(MemberPrincipal.HEADER_EMAIL_VERIFIED,   principal.emailVerified.toString())
                        .header(MemberPrincipal.HEADER_USER_PERMISSIONS, permissionsHeader)
                        // 보안: 하위 서비스에 원본 Authorization 헤더는 유지 (S2S 연계 시 활용)
                        .header("X-Service-Token", jwtProvider.generateS2SToken())
                }
                .build()

            log.debug(
                "[JwtAuthenticationFilter] 인증 성공. memberId={}, role=ROLE_{}, path={}",
                principal.id, principal.memberType.name, path,
            )

            chain.filter(mutatedExchange)
        } catch (e: Exception) {
            log.error(
                "[JwtAuthenticationFilter] 클레임 파싱 중 오류 발생. path={}, error={}",
                path, e.message,
            )
            unauthorized(exchange)
        }
    }

    /// 요청된 경로가 Public Endpoint 목록에 포함되는지 확인합니다.
    /// verify-email은 query parameter가 붙으므로 startsWith로 비교합니다.
    private fun isPublicPath(method: String, path: String): Boolean {
        val key = "$method:$path"
        return PUBLIC_PATHS.any { key.startsWith(it) }
    }

    /// 401 Unauthorized 응답을 즉시 반환합니다.
    /// 인증 실패 시 하위 서비스에 대한 라우팅을 완전히 차단합니다.
    private fun unauthorized(exchange: ServerWebExchange): Mono<Void> {
        exchange.response.statusCode = HttpStatus.UNAUTHORIZED
        return exchange.response.setComplete()
    }

    /// 필터 실행 우선순위. 낮은 값일수록 먼저 실행됩니다.
    /// -1로 설정하여 Spring Cloud Gateway의 기본 필터보다 먼저 인증을 수행합니다.
    override fun getOrder(): Int = -1
}
