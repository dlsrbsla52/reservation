package com.media.bus.contract.filter;

import com.media.bus.contract.security.JwtProvider;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * S2S(Service-to-Service) 토큰 검증 필터 공통 구현체.
 *
 * 내부 서비스(Gateway 등)에서 X-Service-Token 헤더로 S2S 토큰을 전달해야 합니다.
 * 각 마이크로서비스는 이 필터를 빈으로 등록 시 검증이 필요한 경로(applicablePaths)를 주입합니다.
 *
 * 토큰 검증 기준:
 * 1. X-Service-Token 헤더 존재 여부
 * 2. JWT 서명 및 만료 검증
 * 3. type 클레임이 "s2s"인지 확인
 */
@Slf4j
public class S2STokenFilter extends OncePerRequestFilter {

    private static final String S2S_TOKEN_HEADER = "X-Service-Token";
    private static final String S2S_TOKEN_TYPE = "s2s";

    private final JwtProvider jwtProvider;
    private final List<String> applicablePaths;

    public S2STokenFilter(JwtProvider jwtProvider, List<String> applicablePaths) {
        this.jwtProvider = jwtProvider;
        this.applicablePaths = applicablePaths != null ? applicablePaths : Collections.emptyList();
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String token = request.getHeader(S2S_TOKEN_HEADER);

        // 헤더 없거나 유효하지 않은 S2S 토큰 → 401
        if (token == null || !isValidS2SToken(token)) {
            log.warn("[S2STokenFilter] 유효하지 않은 S2S 토큰. URI={}", request.getRequestURI());
            sendUnauthorized(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * JWT 서명 검증 및 type 클레임 확인.
     */
    private boolean isValidS2SToken(String token) {
        try {
            if (jwtProvider.isInvalidToken(token)) {
                return false;
            }
            Claims claims = jwtProvider.parseClaimsFromToken(token);
            return S2S_TOKEN_TYPE.equals(claims.get("type", String.class));
        } catch (Exception e) {
            log.debug("[S2STokenFilter] S2S 토큰 파싱 실패: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 401 Unauthorized 응답을 JSON 형식으로 반환합니다.
     */
    private void sendUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
            "{\"code\":\"00205\",\"message\":\"S2S 토큰 검증에 실패하였습니다.\"}"
        );
    }

    /**
     * 주입받은 경로(applicablePaths) 중 하나라도 일치하면 필터를 적용(false).
     * 어느 것도 일치하지 않으면 필터를 건너뜁니다(true).
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (applicablePaths.isEmpty()) {
            return true;
        }
        String uri = request.getRequestURI();
        return applicablePaths.stream().noneMatch(uri::startsWith);
    }
}
