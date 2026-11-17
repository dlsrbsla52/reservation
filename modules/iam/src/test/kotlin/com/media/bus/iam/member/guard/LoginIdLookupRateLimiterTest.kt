package com.media.bus.iam.member.guard

import com.media.bus.common.exceptions.BusinessException
import com.media.bus.iam.auth.result.AuthResult
import com.media.bus.iam.member.dto.FindMeRequest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.data.redis.RedisConnectionFailureException
import org.springframework.data.redis.core.StringRedisTemplate

class LoginIdLookupRateLimiterTest {
    private val redis = mockk<StringRedisTemplate>()
    private val limiter = LoginIdLookupRateLimiter(redis)
    private val request = FindMeRequest("홍길동", "01000000000", "dummy@example.test")

    @Test
    fun `IP와 각 연락처를 독립 제한하며 Redis 키에 평문을 넣지 않는다`() {
        val keys = mutableListOf<List<String>>()
        every { redis.execute(LoginIdLookupRateLimiter.SCRIPT, capture(keys), *anyVararg()) } returns 1L
        limiter.check("192.0.2.1", request)
        assertThat(keys.flatten()).hasSize(3)
        assertThat(keys.flatten().joinToString()).doesNotContain("192.0.2.1", "01000000000", "dummy@example.test")
        verify(exactly = 1) { redis.execute(LoginIdLookupRateLimiter.SCRIPT, any(), "5", "60") }
        verify(exactly = 2) { redis.execute(LoginIdLookupRateLimiter.SCRIPT, any(), "5", "600") }
    }

    @Test
    fun `IP 제한 초과는 429이며 추가 연락처 키를 만들지 않는다`() {
        every { redis.execute(LoginIdLookupRateLimiter.SCRIPT, any(), *anyVararg()) } returns 0L
        val error = assertThrows<BusinessException> { limiter.check("192.0.2.1", request) }
        assertThat(error.result).isEqualTo(AuthResult.LOGIN_ID_LOOKUP_RATE_LIMITED)
        assertThat(error.httpStatus.value()).isEqualTo(429)
        verify(exactly = 1) { redis.execute(LoginIdLookupRateLimiter.SCRIPT, any(), *anyVararg()) }
    }

    @Test
    fun `Redis 장애는 제한을 우회하지 않는다`() {
        every { redis.execute(LoginIdLookupRateLimiter.SCRIPT, any(), *anyVararg()) } throws RedisConnectionFailureException("테스트 장애")
        assertThrows<RedisConnectionFailureException> { limiter.check("192.0.2.1", request) }
    }
}
