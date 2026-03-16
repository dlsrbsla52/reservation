package com.media.bus.contract.security;

import com.media.bus.common.security.TokenProvider;
import com.media.bus.contract.entity.member.MemberType;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

/**
 * JWT 토큰 생성, 파싱, 검증을 담당하는 공유 컴포넌트.
 * 토큰 전략:
 * - Access Token : 단기(60분), 무상태(Stateless). JWT 서명으로만 검증.
 * - Refresh Token : 장기(7일), Redis에 저장하여 서버 측 무효화(Revoke) 지원.
 * - S2S Token : 시스템 간 내부 통신 전용 토큰. 특수 클레임으로 구분.
 */
@Component
public class JwtProvider implements TokenProvider {

    private static final long ACCESS_TOKEN_EXPIRE_MS = 1000L * 60 * 60; // 60분
    private static final long REFRESH_TOKEN_EXPIRE_MS = 1000L * 60 * 60 * 24 * 7; // 7일
    private static final long S2S_TOKEN_EXPIRE_MS = 1000L * 60 * 60; // 1시간

    private static final String REFRESH_TOKEN_KEY_PREFIX = "refresh:";

    private final SecretKey secretKey;
    private final StringRedisTemplate redisTemplate;

    public JwtProvider(
        @Value("${jwt.secret}") String secret,
        StringRedisTemplate redisTemplate
    ) {
        // JWT secret은 반드시 256bit(32byte) 이상이어야 합니다.
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.redisTemplate = redisTemplate;
    }

    /**
     * 인증된 회원 정보를 기반으로 Access Token을 생성합니다.
     * 클레임에 최소한의 정보만 담아 토큰 크기를 제한합니다.
     */
    public String generateAccessToken(MemberPrincipal principal) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(principal.id())
                .claim(MemberPrincipal.CLAIM_LOGIN_ID, principal.loginId())
                .claim(MemberPrincipal.CLAIM_EMAIL, principal.email())
                .claim(MemberPrincipal.CLAIM_MEMBER_TYPE, principal.memberType().name())
                .claim(MemberPrincipal.CLAIM_EMAIL_VERIFIED, principal.emailVerified())
                .issuedAt(new Date(now))
                .expiration(new Date(now + ACCESS_TOKEN_EXPIRE_MS))
                .signWith(secretKey)
                .compact();
    }

    /**
     * Refresh Token을 생성하고 Redis에 저장합니다.
     * Redis Key: "refresh:{userId}", Value: refreshToken, TTL: 7일
     *
     * @param userId 회원 ID(UUID)
     * @return 생성된 Refresh Token
     */
    public String generateRefreshToken(String userId) {
        long now = System.currentTimeMillis();
        String refreshToken = Jwts.builder()
                .subject(userId)
                .claim("type", "refresh") // Refresh Token 전용 타입 클레임
                .issuedAt(new Date(now))
                .expiration(new Date(now + REFRESH_TOKEN_EXPIRE_MS))
                .signWith(secretKey)
                .compact();

        // Redis에 Refresh Token 저장 (TTL 7일)
        redisTemplate.opsForValue().set(
                REFRESH_TOKEN_KEY_PREFIX + userId,
                refreshToken,
                Duration.ofMillis(REFRESH_TOKEN_EXPIRE_MS));

        return refreshToken;
    }

    /**
     * 시스템 간 내부 호출(S2S)에 사용되는 전용 토큰을 생성합니다.
     * 'system:true' 클레임으로 일반 유저 토큰과 구분하며, 하위 서비스는
     * 이 클레임을 확인하여 S2S 요청에 대한 별도 처리를 적용할 수 있습니다.
     */
    @Override
    public String generateS2SToken() {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject("SYSTEM")
                .claim("type", "s2s")
                .issuedAt(new Date(now))
                .expiration(new Date(now + S2S_TOKEN_EXPIRE_MS))
                .signWith(secretKey)
                .compact();
    }

    /**
     * JWT 토큰의 서명을 검증하고 클레임을 파싱하여 반환합니다.
     *
     * @throws JwtException 서명이 유효하지 않거나 만료된 경우
     */
    public Claims parseClaimsFromToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 토큰 유효성 검사 (서명 + 만료 시간).
     * Gateway Filter에서 빠른 검증을 위해 boolean 반환으로 제공합니다.
     */
    public boolean isInvalidToken(String token) {
        try {
            parseClaimsFromToken(token);
            return false;
        } catch (JwtException | IllegalArgumentException e) {
            return true;
        }
    }

    /**
     * Claims에서 MemberPrincipal 객체를 복원합니다.
     */
    public MemberPrincipal getPrincipalFromClaims(Claims claims) {
        return MemberPrincipal.builder()
                .id(claims.getSubject())
                .loginId(claims.get(MemberPrincipal.CLAIM_LOGIN_ID, String.class))
                .email(claims.get(MemberPrincipal.CLAIM_EMAIL, String.class))
                .memberType(MemberType.valueOf(claims.get(MemberPrincipal.CLAIM_MEMBER_TYPE, String.class)))
                .emailVerified(Boolean.TRUE.equals(claims.get(MemberPrincipal.CLAIM_EMAIL_VERIFIED, Boolean.class)))
                .build();
    }

    /**
     * Redis에서 특정 회원의 Refresh Token을 삭제합니다 (로그아웃 처리).
     */
    public void deleteRefreshToken(String userId) {
        redisTemplate.delete(REFRESH_TOKEN_KEY_PREFIX + userId);
    }

    /**
     * Redis에 저장된 Refresh Token과 요청으로 들어온 토큰을 비교합니다.
     * Token Rotation 또는 Revoke 여부 검증 시 사용합니다.
     */
    public boolean validateRefreshToken(String userId, String refreshToken) {
        String storedToken = redisTemplate.opsForValue().get(REFRESH_TOKEN_KEY_PREFIX + userId);
        return refreshToken.equals(storedToken);
    }
}
