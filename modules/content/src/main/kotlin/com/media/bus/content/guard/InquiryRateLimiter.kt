package com.media.bus.content.guard

import com.media.bus.common.exceptions.BusinessException
import com.media.bus.common.exceptions.ServiceException
import com.media.bus.content.result.ContentResult
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.time.Duration

/** 공개 온라인 문의 IP별 호출 제한 */
@Component
class InquiryRateLimiter(
    private val redisTemplate: StringRedisTemplate,
    @param:Value("\${content.inquiry.rate-limit:5}") private val limit: Int,
    @param:Value("\${content.inquiry.rate-window:10m}") private val window: Duration,
) {
    fun check(clientIp: String?) {
        val digest = MessageDigest.getInstance("SHA-256").digest((clientIp ?: "unknown").toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val allowed = try {
            redisTemplate.execute(SCRIPT, listOf("content:inquiry:ip:$digest"), limit.toString(), window.seconds.toString())
        } catch (e: RuntimeException) {
            throw ServiceException(ContentResult.INQUIRY_RATE_LIMIT_UNAVAILABLE, e)
        } ?: throw ServiceException(ContentResult.INQUIRY_RATE_LIMIT_UNAVAILABLE)
        if (allowed == 0L) throw BusinessException(ContentResult.INQUIRY_RATE_LIMITED)
    }

    companion object {
        internal val SCRIPT = DefaultRedisScript(
            """
            local count = tonumber(redis.call('GET', KEYS[1]) or '0')
            if count >= tonumber(ARGV[1]) then return 0 end
            count = redis.call('INCR', KEYS[1])
            if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[2]) end
            return 1
            """.trimIndent(),
            Long::class.javaObjectType,
        )
    }
}
