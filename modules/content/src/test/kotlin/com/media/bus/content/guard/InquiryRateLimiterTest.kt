package com.media.bus.content.guard

import com.media.bus.common.exceptions.BusinessException
import com.media.bus.common.exceptions.ServiceException
import com.media.bus.content.result.ContentResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.data.redis.core.StringRedisTemplate
import java.time.Duration

class InquiryRateLimiterTest {
    private val redis = mockk<StringRedisTemplate>()
    private val limiter = InquiryRateLimiter(redis, 5, Duration.ofMinutes(10))

    @Test
    fun `IP는 해시로 키를 만들고 허용 횟수와 시간 창을 넘긴다`() {
        val keys = slot<List<String>>()
        every { redis.execute(InquiryRateLimiter.SCRIPT, capture(keys), "5", "600") } returns 1L

        limiter.check("203.0.113.7")

        assertThat(keys.captured.single()).startsWith("content:inquiry:ip:").doesNotContain("203.0.113.7")
    }

    @Test
    fun `허용 횟수를 넘으면 429 코드로 거절한다`() {
        every { redis.execute(InquiryRateLimiter.SCRIPT, any<List<String>>(), any(), any()) } returns 0L
        val ex = assertThrows<BusinessException> { limiter.check("203.0.113.7") }
        assertThat(ex.result).isEqualTo(ContentResult.INQUIRY_RATE_LIMITED)
    }

    @Test
    fun `Redis 장애는 제한 확인 불가 오류로 바꾼다`() {
        every { redis.execute(InquiryRateLimiter.SCRIPT, any<List<String>>(), any(), any()) } throws IllegalStateException()
        val ex = assertThrows<ServiceException> { limiter.check(null) }
        assertThat(ex.result).isEqualTo(ContentResult.INQUIRY_RATE_LIMIT_UNAVAILABLE)
    }
}
