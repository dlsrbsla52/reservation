package com.media.bus.contract.security;

import com.media.bus.common.security.TokenProvider;
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
import java.util.Optional;
import java.util.Set;

/// JWT 토큰 생성, 파싱, 검증을 담당하는 공유 컴포넌트.
/// 토큰 전략:
/// - Access Token : 단기(60분), 무상태(Stateless). JWT 서명으로만 검증.
/// - Refresh Token : 장기(7일), Redis에 저장하여 서버 측 무효화(Revoke) 지원.
/// - S2S Token : 시스템 간 내부 통신 전용 토큰. 특수 클레임으로 구분.
@Component
public class JwtProvider implements TokenProvider {

    private static final long ACCESS_TOKEN_EXPIRE_MS = 1000L * 60 * 60; // 60분
    private static final long REFRESH_TOKEN_EXPIRE_MS = 1000L * 60 * 60 * 24 * 7; // 7일
    private static final long S2S_TOKEN_EXPIRE_MS = 1000L * 60 * 60; // 1시간
    // 만료 5분 전 갱신하여 경계 시점에서 만료된 토큰이 하위 서비스에 전달되는 것을 방지
    private static final long S2S_CACHE_BUFFER_MS = 5 * 60 * 1000L;

    private static final String REFRESH_TOKEN_KEY_PREFIX = "refresh:";

    // S2S 토큰 캐시 — 매 요청마다 HMAC 서명 연산을 방지한다
    private volatile String cachedS2SToken = null;
    private volatile long s2sTokenExpireAt = 0L;

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

    /// 인증된 회원 정보와 DB에서 조회한 권한 목록으로 Access Token을 생성합니다.
    /// permissions claim에 쉼표 구분 문자열로 포함합니다 (예: "READ,WRITE").
    ///
    /// @param principal       인증된 회원 정보
    /// @param permissionNames DB에서 조회한 권한 이름 집합
    public String generateAccessToken(MemberPrincipal principal, Set<String> permissionNames) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(principal.id().toString())
                .claim(MemberPrincipal.CLAIM_LOGIN_ID, principal.loginId())
                .claim(MemberPrincipal.CLAIM_EMAIL, principal.email())
                .claim(MemberPrincipal.CLAIM_MEMBER_TYPE, principal.memberType().name())
                .claim(MemberPrincipal.CLAIM_EMAIL_VERIFIED, principal.emailVerified())
                .claim(MemberPrincipal.CLAIM_PERMISSIONS, String.join(",", permissionNames))
                .issuedAt(new Date(now))
                .expiration(new Date(now + ACCESS_TOKEN_EXPIRE_MS))
                .signWith(secretKey)
                .compact();
    }

    /// Refresh Token을 생성하고 Redis에 저장합니다.
    /// Redis Key: "refresh:{memberId}", Value: refreshToken, TTL: 7일
    ///
    /// @param memberId 회원 ID(UUID)
    /// @return 생성된 Refresh Token
    public String generateRefreshToken(String memberId) {
        long now = System.currentTimeMillis();
        String refreshToken = Jwts.builder()
                .subject(memberId)
                .claim("type", "refresh") // Refresh Token 전용 타입 클레임
                .issuedAt(new Date(now))
                .expiration(new Date(now + REFRESH_TOKEN_EXPIRE_MS))
                .signWith(secretKey)
                .compact();

        // Redis에 Refresh Token 저장 (TTL 7일)
        redisTemplate.opsForValue().set(
                REFRESH_TOKEN_KEY_PREFIX + memberId,
                refreshToken,
                Duration.ofMillis(REFRESH_TOKEN_EXPIRE_MS));

        return refreshToken;
    }

    /// 시스템 간 내부 호출(S2S)에 사용되는 전용 토큰을 반환합니다.
    /// TTL 1시간 토큰을 double-checked locking으로 캐싱하여 매 요청마다
    /// HMAC 서명 연산이 발생하는 것을 방지합니다.
    /// 만료 5분 전에 미리 갱신하여 경계 시점 만료를 방지합니다.
    @Override
    public String generateS2SToken() {
        long now = System.currentTimeMillis();
        if (cachedS2SToken == null || now >= s2sTokenExpireAt - S2S_CACHE_BUFFER_MS) {
            synchronized (this) {
                if (cachedS2SToken == null || now >= s2sTokenExpireAt - S2S_CACHE_BUFFER_MS) {
                    cachedS2SToken = buildS2SToken(now);
                    s2sTokenExpireAt = now + S2S_TOKEN_EXPIRE_MS;
                }
            }
        }
        return cachedS2SToken;
    }

    private String buildS2SToken(long now) {
        return Jwts.builder()
                .subject("SYSTEM")
                .claim("type", "s2s")
                .issuedAt(new Date(now))
                .expiration(new Date(now + S2S_TOKEN_EXPIRE_MS))
                .signWith(secretKey)
                .compact();
    }

    /// JWT 토큰의 서명을 검증하고 클레임을 파싱하여 반환합니다.
    ///
    /// @throws JwtException 서명이 유효하지 않거나 만료된 경우
    public Claims parseClaimsFromToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /// JWT 토큰을 파싱하여 Claims를 반환한다.
    /// 서명 오류 또는 만료 시 빈 Optional 반환 (예외 미전파).
    /// Gateway Filter에서 isInvalidToken() + parseClaimsFromToken() 이중 파싱을 단일 호출로 대체한다.
    ///
    /// @param token JWT 토큰 문자열
    /// @return 파싱된 Claims, 실패 시 Optional.empty()
    public Optional<Claims> tryParseClaims(String token) {
        try {
            return Optional.of(parseClaimsFromToken(token));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /// 토큰 유효성 검사 (서명 + 만료 시간).
    /// tryParseClaims()를 재사용하여 이중 파싱을 제거합니다.
    public boolean isInvalidToken(String token) {
        return tryParseClaims(token).isEmpty();
    }

    /// jwt 토큰을 기반으로 Claims 반환
    ///
    /// @param jwtToken JWT 토큰
    /// @return Claims
    public Claims getClaimsFromRefreshToken(String jwtToken) {

        return parseClaimsFromToken(jwtToken);
    }

    /// jwt 토큰을 기반으로 Claims 반환
    ///
    /// @param jwtToken JWT 토큰
    /// @return Claims
    public MemberPrincipal getMemberPrincipalFromRefreshToken(String jwtToken) {
        String token = jwtToken.startsWith("Bearer ") ? jwtToken.substring(7) : jwtToken;
        Claims claimsFromRefreshToken = getClaimsFromRefreshToken(token);

        return getPrincipalFromClaims(claimsFromRefreshToken);
    }

    

    /// Claims에서 MemberPrincipal 객체를 복원합니다.
    /// MemberPrincipal.fromClaims()에 위임합니다.
    public MemberPrincipal getPrincipalFromClaims(Claims claims) {
        return MemberPrincipal.fromClaims(claims);
    }

    /// Redis에서 특정 회원의 Refresh Token을 삭제합니다 (로그아웃 처리).
    public void deleteRefreshToken(String memberId) {
        redisTemplate.delete(REFRESH_TOKEN_KEY_PREFIX + memberId);
    }

    /// Redis에 저장된 Refresh Token과 요청으로 들어온 토큰을 비교합니다.
    /// Token Rotation 또는 Revoke 여부 검증 시 사용합니다.
    public boolean validateRefreshToken(String memberId, String refreshToken) {
        String storedToken = redisTemplate.opsForValue().get(REFRESH_TOKEN_KEY_PREFIX + memberId);
        return refreshToken.equals(storedToken);
    }
}
