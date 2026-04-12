package com.media.bus.contract.security

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import com.media.bus.common.security.TokenProvider
import io.jsonwebtoken.Claims
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * ## JWT 토큰 생성, 파싱, 검증을 담당하는 공유 컴포넌트
 *
 * 토큰 전략:
 * - Access Token : 단기(60분), 무상태(Stateless). JWT 서명으로만 검증.
 * - Refresh Token : 장기(7일), Redis에 **세션 단위 Hash**로 저장하여
 *   다중 디바이스 세션 관리 + 서버 측 개별 무효화(Revoke)를 지원한다.
 * - S2S Token : 시스템 간 내부 통신 전용 토큰. 특수 클레임으로 구분.
 *
 * **Refresh Token 저장 구조 (Redis Hash)**
 * - Key: `refresh:{memberId}`
 * - Field: `sessionId (UUID)` — Refresh Token JWT의 `sid` claim과 매칭
 * - Value: [SessionInfo] JSON (refreshToken, deviceInfo, ip, issuedAt, lastAccessedAt)
 * - TTL: 7일 (write 시마다 슬라이딩)
 *
 * 단일 디바이스 로그인 가정에서 다중 디바이스로 확장되었으므로, 기존 `generateRefreshToken(memberId)`
 * 시그니처는 하위 호환을 위해 유지한다 — 내부적으로 새 세션 ID를 자동 생성한다.
 */
@Component
class JwtProvider(
    @Value("\${jwt.secret}") secret: String,
    private val redisTemplate: StringRedisTemplate,
) : TokenProvider {

    companion object {
        private const val ACCESS_TOKEN_EXPIRE_MS = 1000L * 60 * 60          // 60분
        private const val REFRESH_TOKEN_EXPIRE_MS = 1000L * 60 * 60 * 24 * 7 // 7일
        private const val S2S_TOKEN_EXPIRE_MS = 1000L * 60 * 60              // 1시간
        // 만료 5분 전 갱신하여 경계 시점에서 만료된 토큰이 하위 서비스에 전달되는 것을 방지
        private const val S2S_CACHE_BUFFER_MS = 5 * 60 * 1000L

        private const val REFRESH_TOKEN_KEY_PREFIX = "refresh:"

        /** Refresh Token 전용 claim key — Redis 세션 식별자(UUID). */
        const val SESSION_ID_CLAIM = "sid"
    }

    /**
     * Redis에 저장되는 단일 Refresh Token 세션의 메타데이터.
     * `refreshToken` 자체는 탈취 방지를 위해 저장소에서만 확인하고 응답에는 포함하지 않는다.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class SessionInfo(
        val refreshToken: String = "",
        val deviceInfo: String? = null,
        val ip: String? = null,
        val issuedAt: Long = 0L,
        val lastAccessedAt: Long = 0L,
    )

    // JWT secret은 반드시 256bit(32byte) 이상이어야 합니다.
    private val secretKey = Keys.hmacShaKeyFor(secret.toByteArray(StandardCharsets.UTF_8))

    /** Redis Hash 값 직렬화용 — 기본 설정으로 충분. */
    private val objectMapper = ObjectMapper()

    // S2S 토큰 캐시 — 매 요청마다 HMAC 서명 연산을 방지한다
    // ReentrantLock: Virtual Thread 환경에서 synchronized 사용 시 캐리어 스레드 핀닝이 발생하므로 Lock으로 대체
    private val s2sLock = ReentrantLock()

    @Volatile
    private var cachedS2SToken: String? = null

    @Volatile
    private var s2sTokenExpireAt: Long = 0L

    /**
     * 인증된 회원 정보와 DB에서 조회한 권한 목록으로 Access Token을 생성합니다.
     */
    fun generateAccessToken(principal: MemberPrincipal, permissionNames: Set<String>): String {
        val now = System.currentTimeMillis()
        return Jwts.builder()
            .subject(principal.id.toString())
            .claim(MemberPrincipal.CLAIM_LOGIN_ID, principal.loginId)
            .claim(MemberPrincipal.CLAIM_EMAIL, principal.email)
            .claim(MemberPrincipal.CLAIM_MEMBER_TYPE, principal.memberType.name)
            .claim(MemberPrincipal.CLAIM_EMAIL_VERIFIED, principal.emailVerified)
            .claim(MemberPrincipal.CLAIM_PERMISSIONS, permissionNames.joinToString(","))
            .issuedAt(Date(now))
            .expiration(Date(now + ACCESS_TOKEN_EXPIRE_MS))
            .signWith(secretKey)
            .compact()
    }

    /**
     * Refresh Token을 생성하고 Redis에 신규 세션으로 저장합니다.
     * 하위 호환용 — 내부적으로 새 sessionId(UUID)를 생성합니다.
     *
     * @param memberId 회원 ID(UUID 문자열)
     * @return 생성된 Refresh Token
     */
    fun generateRefreshToken(memberId: String): String =
        generateRefreshToken(memberId, UUID.randomUUID().toString(), null, null)

    /**
     * Refresh Token을 생성하고 Redis에 지정된 세션으로 저장합니다.
     *
     * @param memberId    회원 ID(UUID 문자열)
     * @param sessionId   세션 식별자 — 재발급 시 동일 값을 전달하면 해당 세션이 rotation 된다
     * @param deviceInfo  User-Agent 등 기기 정보 (선택)
     * @param ip          클라이언트 IP (선택)
     */
    fun generateRefreshToken(
        memberId: String,
        sessionId: String,
        deviceInfo: String?,
        ip: String?,
    ): String {
        val now = System.currentTimeMillis()
        val refreshToken = Jwts.builder()
            .subject(memberId)
            .claim("type", "refresh")
            .claim(SESSION_ID_CLAIM, sessionId)
            .issuedAt(Date(now))
            .expiration(Date(now + REFRESH_TOKEN_EXPIRE_MS))
            .signWith(secretKey)
            .compact()

        // 기존 세션의 issuedAt을 유지하여 "언제 로그인했는지"를 보존한다.
        val key = REFRESH_TOKEN_KEY_PREFIX + memberId
        val existing = readSession(key, sessionId)
        val info = SessionInfo(
            refreshToken = refreshToken,
            deviceInfo = deviceInfo ?: existing?.deviceInfo,
            ip = ip ?: existing?.ip,
            issuedAt = existing?.issuedAt ?: now,
            lastAccessedAt = now,
        )
        redisTemplate.opsForHash<String, String>().put(key, sessionId, writeSession(info))
        // Hash 전체 TTL은 마지막 write 기준 7일로 슬라이딩
        redisTemplate.expire(key, Duration.ofMillis(REFRESH_TOKEN_EXPIRE_MS))
        return refreshToken
    }

    /**
     * 시스템 간 내부 호출(S2S)에 사용되는 전용 토큰을 반환합니다.
     */
    override fun generateS2SToken(): String {
        val now = System.currentTimeMillis()
        if (cachedS2SToken == null || now >= s2sTokenExpireAt - S2S_CACHE_BUFFER_MS) {
            s2sLock.withLock {
                if (cachedS2SToken == null || now >= s2sTokenExpireAt - S2S_CACHE_BUFFER_MS) {
                    cachedS2SToken = buildS2SToken(now)
                    s2sTokenExpireAt = now + S2S_TOKEN_EXPIRE_MS
                }
            }
        }
        return cachedS2SToken!!
    }

    private fun buildS2SToken(now: Long): String =
        Jwts.builder()
            .subject("SYSTEM")
            .claim("type", "s2s")
            .issuedAt(Date(now))
            .expiration(Date(now + S2S_TOKEN_EXPIRE_MS))
            .signWith(secretKey)
            .compact()

    /** JWT 토큰의 서명을 검증하고 클레임을 파싱하여 반환합니다. */
    fun parseClaimsFromToken(token: String): Claims =
        Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .payload

    /**
     * JWT 토큰을 파싱하여 Claims를 반환한다.
     * 서명 오류 또는 만료 시 null 반환 (예외 미전파).
     */
    fun tryParseClaims(token: String): Claims? =
        try {
            parseClaimsFromToken(token)
        } catch (e: JwtException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }

    /** 토큰 유효성 검사 (서명 + 만료 시간). */
    fun isInvalidToken(token: String): Boolean = tryParseClaims(token) == null

    fun getClaimsFromRefreshToken(jwtToken: String): Claims = parseClaimsFromToken(jwtToken)

    fun getMemberPrincipalFromRefreshToken(jwtToken: String): MemberPrincipal {
        val token = if (jwtToken.startsWith("Bearer ")) jwtToken.substring(7) else jwtToken
        val claims = getClaimsFromRefreshToken(token)
        return getPrincipalFromClaims(claims)
    }

    fun getPrincipalFromClaims(claims: Claims): MemberPrincipal = MemberPrincipal.fromClaims(claims)

    // ─────────────────────────────────────────────────────────────────
    // Refresh Token — 세션 기반 저장/검증/삭제
    // ─────────────────────────────────────────────────────────────────

    /**
     * 해당 회원의 **모든 세션**을 삭제한다.
     * 비밀번호 변경, 관리자 정지, 탈퇴 등 전 디바이스 강제 로그아웃이 필요한 시점에 사용한다.
     */
    fun deleteRefreshToken(memberId: String) {
        redisTemplate.delete(REFRESH_TOKEN_KEY_PREFIX + memberId)
    }

    /** 특정 세션 하나만 삭제한다 (개별 디바이스 로그아웃). */
    fun deleteSession(memberId: String, sessionId: String) {
        redisTemplate.opsForHash<String, String>().delete(REFRESH_TOKEN_KEY_PREFIX + memberId, sessionId)
    }

    /**
     * Redis에 저장된 Refresh Token과 요청 토큰을 비교한다 (세션 ID 지정).
     * Token Rotation / Revoke 검증 시 사용한다.
     */
    fun validateRefreshToken(memberId: String, sessionId: String, refreshToken: String): Boolean {
        val info = readSession(REFRESH_TOKEN_KEY_PREFIX + memberId, sessionId) ?: return false
        return info.refreshToken == refreshToken
    }

    /**
     * 하위 호환 — 세션 ID를 모르는 호출부를 위해 Hash 내 모든 세션에서 일치하는 토큰을 탐색한다.
     * 신규 호출부는 sessionId를 명시한 오버로드를 사용할 것.
     */
    fun validateRefreshToken(memberId: String, refreshToken: String): Boolean {
        val key = REFRESH_TOKEN_KEY_PREFIX + memberId
        val entries = redisTemplate.opsForHash<String, String>().entries(key)
        return entries.values.any { json -> readSessionSafe(json)?.refreshToken == refreshToken }
    }

    /**
     * 회원의 활성 세션 전체를 반환한다.
     * 반환 객체의 `refreshToken`은 민감 정보이므로 외부 응답 DTO로 노출할 때 제외해야 한다.
     */
    fun listSessions(memberId: String): Map<String, SessionInfo> {
        val entries = redisTemplate.opsForHash<String, String>().entries(REFRESH_TOKEN_KEY_PREFIX + memberId)
        return entries.mapNotNull { (field, json) ->
            val sid = field?.toString() ?: return@mapNotNull null
            val info = readSessionSafe(json?.toString() ?: return@mapNotNull null) ?: return@mapNotNull null
            sid to info
        }.toMap()
    }

    // ─────────────────────────────────────────────────────────────────
    // 내부 직렬화 유틸
    // ─────────────────────────────────────────────────────────────────

    private fun readSession(key: String, sessionId: String): SessionInfo? {
        val json = redisTemplate.opsForHash<String, String>().get(key, sessionId) ?: return null
        return readSessionSafe(json.toString())
    }

    private fun readSessionSafe(json: String): SessionInfo? =
        try {
            objectMapper.readValue(json, SessionInfo::class.java)
        } catch (e: Exception) {
            null
        }

    private fun writeSession(info: SessionInfo): String = objectMapper.writeValueAsString(info)
}
