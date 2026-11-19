package com.media.bus.contract.security

import com.media.bus.contract.entity.member.MemberType
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.data.redis.core.HashOperations
import org.springframework.data.redis.core.StringRedisTemplate
import java.nio.charset.StandardCharsets
import java.util.*

/**
 * 토큰 페이로드에 회원 정보가 **평문으로 남지 않는지**와, 파싱 후에는 평문으로 복원되는지를 고정한다.
 */
class JwtProviderClaimEncryptionTest {

    // refresh 토큰 발급은 기존 세션을 Redis Hash 에서 읽으므로, 빈 세션을 돌려주도록 고정한다.
    private val hashOps = mockk<HashOperations<String, String, String>>(relaxed = true).also {
        every { it.get(any(), any()) } returns null
    }
    private val redisTemplate = mockk<StringRedisTemplate>(relaxed = true).also {
        every { it.opsForHash<String, String>() } returns hashOps
    }

    private val jwtProvider = JwtProvider(
        secret = "local-dev-dummy-jwt-secret-for-tests-only-0000000000",
        claimEncryptionKey = "dGVzdC1vbmx5LWR1bW15LWNsYWltLWtleS0zMmJ5dGU=",
        redisTemplate = redisTemplate,
    )

    private val principal = MemberPrincipal(
        id = UUID.randomUUID(),
        loginId = "secret-login",
        email = "secret@example.com",
        memberType = MemberType.ADMIN_MASTER,
        emailVerified = true,
        permissions = emptySet(),
    )

    /** 서명 검증 없이 JWS 페이로드 JSON을 그대로 읽는다 — 토큰을 탈취한 사람이 보는 내용. */
    private fun rawPayload(token: String): String =
        String(Base64.getUrlDecoder().decode(token.split(".")[1]), StandardCharsets.UTF_8)

    @Test
    fun `액세스 토큰 페이로드에 sub 와 회원 정보가 평문으로 보이지 않는다`() {
        val token = jwtProvider.generateAccessToken(principal, setOf("READ", "MANAGE"), TokenAudience.ADMIN)
        val payload = rawPayload(token)

        assertThat(payload).doesNotContain(
            principal.id.toString(), "secret-login", "secret@example.com", "ADMIN_MASTER", "MANAGE",
        )
        // aud 는 평문이다 — 필터가 발급처를 판별한다
        assertThat(payload).contains("\"aud\"", "ADMIN")
    }

    @Test
    fun `파싱하면 원래 회원 정보로 복원된다`() {
        val token = jwtProvider.generateAccessToken(principal, setOf("READ", "MANAGE"), TokenAudience.ADMIN)

        val restored = jwtProvider.getPrincipalFromClaims(jwtProvider.parseClaimsFromToken(token))

        assertThat(restored.id).isEqualTo(principal.id)
        assertThat(restored.loginId).isEqualTo("secret-login")
        assertThat(restored.email).isEqualTo("secret@example.com")
        assertThat(restored.memberType).isEqualTo(MemberType.ADMIN_MASTER)
        assertThat(restored.emailVerified).isTrue()
        assertThat(restored.permissions.map { it.name }).containsExactlyInAnyOrder("READ", "MANAGE")
        assertThat(restored.audience).isEqualTo(TokenAudience.ADMIN)
    }

    @Test
    fun `refresh 토큰은 sub 만 암호화하고 sid 와 type 은 평문으로 둔다`() {
        val memberId = UUID.randomUUID().toString()
        val token = jwtProvider.generateRefreshToken(memberId, "sid-1", null, null)

        assertThat(rawPayload(token)).doesNotContain(memberId).contains("sid-1", "refresh")
        val claims = jwtProvider.parseClaimsFromToken(token)
        assertThat(claims.subject).isEqualTo(memberId)
        assertThat(claims[JwtProvider.SESSION_ID_CLAIM]).isEqualTo("sid-1")
    }
}
