package com.media.bus.contract.security

import com.media.bus.contract.entity.member.MemberType
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import org.springframework.data.redis.core.HashOperations
import org.springframework.data.redis.core.StringRedisTemplate
import java.time.Duration
import java.util.*

/**
 * 액세스 토큰의 **발급처(`aud`)와 TTL 계약**을 고정한다.
 * gateway 는 `aud` 로 관리자 경로 진입을 판단하므로, 클레임 이름·값이 바뀌면 관리자 화면 전체가 403 이 된다.
 */
class JwtProviderAudienceTest {

    // refresh 토큰 발급은 기존 세션을 Redis Hash 에서 읽으므로, 빈 세션을 돌려주도록 고정한다.
    private val hashOps = mockk<HashOperations<String, String, String>>(relaxed = true).also {
        every { it.get(any(), any()) } returns null
    }
    private val redisTemplate = mockk<StringRedisTemplate>(relaxed = true).also {
        every { it.opsForHash<String, String>() } returns hashOps
    }

    private val jwtProvider = JwtProvider(
        secret = "local-dev-dummy-jwt-secret-for-tests-only-0000000000",
        redisTemplate = redisTemplate,
    )

    private val principal = MemberPrincipal(
        id = UUID.randomUUID(),
        loginId = "admin",
        email = "admin@example.com",
        memberType = MemberType.ADMIN_MASTER,
        emailVerified = true,
        permissions = emptySet(),
    )

    private fun ttlOf(token: String): Duration {
        val claims = jwtProvider.parseClaimsFromToken(token)
        return Duration.ofMillis(claims.expiration.time - claims.issuedAt.time)
    }

    @Test
    fun `관리자 발급처 토큰은 aud=ADMIN 이고 TTL 15분이다`() {
        val token = jwtProvider.generateAccessToken(principal, setOf("MANAGE"), TokenAudience.ADMIN)
        val claims = jwtProvider.parseClaimsFromToken(token)

        assertThat(TokenAudience.fromClaims(claims)).isEqualTo(TokenAudience.ADMIN)
        assertThat(ttlOf(token).toSeconds()).isCloseTo(Duration.ofMinutes(15).toSeconds(), within(1L))
    }

    @Test
    fun `발급처를 지정하지 않으면 aud=USER 이고 TTL 60분이다`() {
        val token = jwtProvider.generateAccessToken(principal, emptySet())
        val claims = jwtProvider.parseClaimsFromToken(token)

        assertThat(TokenAudience.fromClaims(claims)).isEqualTo(TokenAudience.USER)
        assertThat(ttlOf(token).toSeconds()).isCloseTo(Duration.ofMinutes(60).toSeconds(), within(1L))
    }

    @Test
    fun `refresh 토큰도 발급처를 기록한다`() {
        val token = jwtProvider.generateRefreshToken(principal.id.toString(), "sid-1", null, null, TokenAudience.ADMIN)

        assertThat(TokenAudience.fromClaims(jwtProvider.parseClaimsFromToken(token))).isEqualTo(TokenAudience.ADMIN)
    }

    @Test
    fun `aud 가 없는 구버전 토큰은 USER 로 간주한다`() {
        val legacy = io.jsonwebtoken.Jwts.claims().subject("x").build()

        assertThat(TokenAudience.fromClaims(legacy)).isEqualTo(TokenAudience.USER)
    }
}
