package com.gateway.gateway.filter;

import com.contract.security.JwtProvider;
import com.contract.security.MemberPrincipal;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Gateway Edge Authentication Filter.
 * 모든 인입 요청에 대해 JWT Bearer 토큰 검증을 수행하는 Global Filter입니다.
 * 처리 흐름:
 * 1. 요청 경로가 Public Whitelist에 포함된 경우: 토큰 검증 없이 통과.
 * 2. Authorization 헤더에서 Bearer 토큰을 추출.
 * 3. JwtProvider로 서명 및 만료 검증.
 * 4. 검증 성공 시: 클레임 추출 → X-User-* 헤더 주입 → 하위 서비스로 라우팅.
 * 5. 검증 실패 시: 401 Unauthorized 즉시 응답 (하위 서비스에 요청 도달 차단).
 * Gateway는 Reactive(WebFlux) 기반이므로 GlobalFilter를 Reactor 방식으로 구현합니다.
 */
@Slf4j
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    /**
     * JWT 검증을 건너뛰는 Public Endpoint 목록.
     * HTTP Method + Path 쌍으로 정의합니다.
     */
    private static final List<String> PUBLIC_PATHS = List.of(
        "POST:/api/v1/auth/register",
        "POST:/api/v1/auth/login",
        "POST:/api/v1/auth/token/refresh",
        "GET:/api/v1/auth/verify-email",
        "GET:/api/v1/auth/health-check",
        "GET:/api/v1/member/health-check",
        "GET:/api/v1/reservation/health-check"
    );

    // 하위 서비스로 전달할 사용자 컨텍스트 헤더 이름 상수
    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USER_ROLE = "X-User-Role";
    public static final String HEADER_EMAIL_VERIFIED = "X-Email-Verified";

    private final JwtProvider jwtProvider;

    public JwtAuthenticationFilter(JwtProvider jwtProvider) {
        this.jwtProvider = jwtProvider;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String method = exchange.getRequest().getMethod().name();
        String path = exchange.getRequest().getURI().getPath();

        // Public Endpoint는 인증 없이 통과
        if (isPublicPath(method, path)) {
            return chain.filter(exchange);
        }

        // Authorization 헤더에서 Bearer 토큰 추출
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("[JwtAuthenticationFilter] Authorization 헤더 없음. path={}", path);
            return unauthorized(exchange);
        }

        String token = authHeader.substring(7);

        // JWT 토큰 검증 (서명 + 만료)
        if (jwtProvider.isInvalidToken(token)) {
            log.warn("[JwtAuthenticationFilter] 유효하지 않은 토큰. path={}", path);
            return unauthorized(exchange);
        }

        // 클레임 추출 및 하위 서비스로 전달할 헤더 주입
        try {
            Claims claims = jwtProvider.parseClaimsFromToken(token);
            MemberPrincipal principal = jwtProvider.getPrincipalFromClaims(claims);

            // 하위 서비스는 이 헤더를 신뢰하여 별도 JWT 파싱 없이 사용자 정보를 활용합니다.
            ServerWebExchange mutatedExchange = exchange.mutate()
                    .request(r -> r
                            .header(HEADER_USER_ID, principal.id())
                            .header(HEADER_USER_ROLE, "ROLE_" + principal.memberType().name())
                            .header(HEADER_EMAIL_VERIFIED, String.valueOf(principal.emailVerified()))
                    // 보안: 하위 서비스에 원본 Authorization 헤더는 유지 (S2S 연계 시 활용)
                    )
                    .build();

            log.debug("[JwtAuthenticationFilter] 인증 성공. userId={}, role=ROLE_{}, path={}",
                    principal.id(), principal.memberType().name(), path);

            return chain.filter(mutatedExchange);

        } catch (Exception e) {
            log.error("[JwtAuthenticationFilter] 클레임 파싱 중 오류 발생. path={}, error={}", path, e.getMessage());
            return unauthorized(exchange);
        }
    }

    /**
     * 요청된 경로가 Public Endpoint 목록에 포함되는지 확인합니다.
     * verify-email은 query parameter가 붙으므로 startsWith로 비교합니다.
     */
    private boolean isPublicPath(String method, String path) {
        String key = method + ":" + path;
        return PUBLIC_PATHS.stream().anyMatch(key::startsWith);
    }

    /**
     * 401 Unauthorized 응답을 즉시 반환합니다.
     * 인증 실패 시 하위 서비스에 대한 라우팅을 완전히 차단합니다.
     */
    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    /**
     * 필터 실행 우선순위. 낮은 값일수록 먼저 실행됩니다.
     * -1로 설정하여 Spring Cloud Gateway의 기본 필터보다 먼저 인증을 수행합니다.
     */
    @Override
    public int getOrder() {
        return -1;
    }
}
