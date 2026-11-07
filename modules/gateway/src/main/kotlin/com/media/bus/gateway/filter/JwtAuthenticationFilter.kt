package com.media.bus.gateway.filter

import com.media.bus.contract.security.JwtProvider
import com.media.bus.contract.security.MemberPrincipal
import com.media.bus.contract.security.TokenAudience
import io.jsonwebtoken.Claims
import org.slf4j.LoggerFactory
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.filter.GlobalFilter
import org.springframework.cloud.gateway.route.Route
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils
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
            "POST:/api/v1/auth/admin/login",
            "POST:/api/v1/auth/admin/token/refresh",
            "POST:/api/v1/auth/admin/reactivate",
            // 로그인하지 못한 사용자가 쓰는 복구 흐름 — 요청 body 로만 본인을 확인하며 X-User-* 헤더를 읽지 않는다.
            "POST:/api/v1/auth/reactivate",
            "POST:/api/v1/auth/password-reset/request",
            "POST:/api/v1/auth/password-reset/verify",
            "POST:/api/v1/auth/password-reset/confirm",
            "GET:/api/v1/auth/verify-email",
            "GET:/api/v1/auth/health-check",
            "GET:/api/v1/member/health-check",
            "GET:/api/v1/reservation/health-check",
            "GET:/api/v1/stop/health-check",
            "GET:/api/v1/member/find/me"
        )

        /// 라우트 metadata 키. 값은 [TokenAudience] 이름이며, 해당 발급처 토큰만 이 라우트로 보낸다.
        const val REQUIRED_AUDIENCE_METADATA = "required-audience"

        /// 라우트 metadata 가 빠져도 관리자 경로는 막기 위한 경로 기준 2차 판정 prefix.
        private const val ADMIN_PATH_PREFIX = "/api/v1/admin"

        private val MULTIPLE_SLASHES = Regex("/{2,}")

        /// 하위 서비스가 신뢰하는 사용자 컨텍스트 헤더. gateway 만 주입해야 한다.
        private val TRUSTED_USER_HEADERS = listOf(
            MemberPrincipal.HEADER_USER_ID,
            MemberPrincipal.HEADER_USER_LOGIN_ID,
            MemberPrincipal.HEADER_USER_EMAIL,
            MemberPrincipal.HEADER_USER_ROLE,
            MemberPrincipal.HEADER_EMAIL_VERIFIED,
            MemberPrincipal.HEADER_USER_PERMISSIONS,
        )
    }

    override fun filter(exchange: ServerWebExchange, chain: GatewayFilterChain): Mono<Void> {
        val method = exchange.request.method.name()
        val path = exchange.request.uri.path

        // Public Endpoint는 인증 없이 통과.
        // 단, 클라이언트가 넣은 사용자 컨텍스트 헤더는 지운다 — 하위 서비스는 이 헤더를 검증 없이 신뢰하므로
        // 공개 엔드포인트가 나중에 이 헤더를 읽게 되면 그대로 위조 경로가 된다.
        if (isPublicPath(method, path)) {
            val sanitized = exchange.mutate()
                .request { r -> r.headers { headers -> TRUSTED_USER_HEADERS.forEach(headers::remove) } }
                .build()
            return chain.filter(sanitized)
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

            // 발급처 경계: 관리자 라우트는 관리자 계정 + 관리자 사이트(aud=ADMIN) 토큰만 통과시킨다.
            // 하위 서비스의 @Authorize 와 별개로 gateway 에서 먼저 막아, 컨트롤러 인가 누락 시에도
            // 일반 사이트 토큰이 관리자 API 에 도달하지 못하게 한다.
            val requiredAudience = resolveRequiredAudience(exchange, path)
            if (requiredAudience != null && !isAudienceSatisfied(principal, claims, requiredAudience)) {
                log.warn(
                    "[JwtAuthenticationFilter] 발급처 불일치로 차단. memberId={}, memberType={}, required={}, path={}",
                    principal.id, principal.memberType.name, requiredAudience, path,
                )
                return forbidden(exchange)
            }

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

    /// 이 요청에 필요한 토큰 발급처를 결정합니다.
    /// 1차로 매칭된 라우트의 metadata 를 보고, metadata 가 없어도 관리자 경로면 ADMIN 을 요구합니다.
    /// (라우트 설정 실수로 metadata 가 빠지는 경우를 막는 안전망. `//` 중복 슬래시는 접어서 비교합니다)
    private fun resolveRequiredAudience(exchange: ServerWebExchange, path: String): TokenAudience? {
        val route = exchange.getAttribute<Route>(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR)
        val metadataValue = route?.metadata?.get(REQUIRED_AUDIENCE_METADATA)
        if (metadataValue != null) {
            // 설정 오타(예: 소문자 admin)로 검사가 조용히 꺼지지 않도록, 알 수 없는 값은 가장 엄격한 ADMIN 으로 본다(fail-closed).
            return TokenAudience.fromName(metadataValue.toString()) ?: TokenAudience.ADMIN.also {
                log.error(
                    "[JwtAuthenticationFilter] 알 수 없는 {} 값 '{}' — ADMIN 으로 처리합니다. routeId={}",
                    REQUIRED_AUDIENCE_METADATA, metadataValue, route.id,
                )
            }
        }

        val normalized = path.replace(MULTIPLE_SLASHES, "/")
        val isAdminPath = normalized == ADMIN_PATH_PREFIX || normalized.startsWith("$ADMIN_PATH_PREFIX/")
        return if (isAdminPath) TokenAudience.ADMIN else null
    }

    /// 관리자 발급처는 토큰의 memberType 이 관리자 카테고리인지도 함께 확인합니다(발급 규칙 위반 토큰에 대한 방어).
    /// memberType 은 **발급 시점의 클레임**이라 발급 이후의 역할 변경은 감지하지 못합니다.
    /// 해임된 관리자는 액세스 토큰 TTL(관리자 15분) 동안 통과할 수 있고, 갱신은 iam 이 거부합니다.
    private fun isAudienceSatisfied(
        principal: MemberPrincipal,
        claims: Claims,
        required: TokenAudience,
    ): Boolean {
        val audience = TokenAudience.fromClaims(claims)
        return when (required) {
            TokenAudience.ADMIN -> audience == TokenAudience.ADMIN && principal.memberType.isAdmin
            TokenAudience.USER -> audience == TokenAudience.USER
        }
    }

    /// 403 Forbidden 응답을 즉시 반환합니다. 인증은 됐지만 이 경로에 쓸 수 없는 토큰입니다.
    private fun forbidden(exchange: ServerWebExchange): Mono<Void> {
        exchange.response.statusCode = HttpStatus.FORBIDDEN
        return exchange.response.setComplete()
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
