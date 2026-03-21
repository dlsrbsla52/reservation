package com.media.bus.auth.configuration.filter;

import com.media.bus.contract.security.JwtProvider;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * S2S(Service-to-Service) 토큰 검증 필터.
 * /api/v1/member/** 경로에만 적용됩니다.
 *
 * 내부 서비스(Gateway 등)에서 X-Service-Token 헤더로 S2S 토큰을 전달해야 합니다.
 * 토큰 검증 기준:
 * 1. X-Service-Token 헤더 존재 여부
 * 2. JWT 서명 및 만료 검증
 * 3. type 클레임이 "s2s"인지 확인
 */
@Slf4j
@RequiredArgsConstructor
public class S2STokenFilter extends OncePerRequestFilter {

    private static final String S2S_TOKEN_HEADER = "X-Service-Token";
    private static final String S2S_TOKEN_TYPE = "s2s";

    private final JwtProvider jwtProvider;

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
            "{\"code\":\"00205\",\"message\":\"토큰 검증에 실패하였습니다.\"}"
        );
    }

    /**
     * /api/v1/member/** 경로에만 필터를 적용합니다.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/member/");
    }
}
