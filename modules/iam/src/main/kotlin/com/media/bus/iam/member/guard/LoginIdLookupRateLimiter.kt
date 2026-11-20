package com.media.bus.iam.member.guard

import com.media.bus.common.exceptions.BusinessException
import com.media.bus.common.exceptions.ServiceException
import com.media.bus.iam.auth.result.AuthResult
import com.media.bus.iam.member.dto.FindMeRequest
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.util.*

/**
 * ## 공개 아이디 찾기 호출 제한
 *
 * IP별 1분 5회, 연락처별 10분 5회로 제한한다. 실패 조회도 횟수에 포함한다.
 * Redis에서 증가와 만료를 원자적으로 수행하여 여러 앱 인스턴스에서도 같은 제한을 적용한다.
 * 연락처와 IP는 해시로 키에 저장한다. Redis 장애 시 조회를 허용하지 않는다.
 */
@Component
class LoginIdLookupRateLimiter(private val redisTemplate: StringRedisTemplate) {
    fun check(clientIp: String?, request: FindMeRequest) {
        consume("ip", clientIp ?: "unknown", 60)
        request.phoneNumber?.takeIf { it.isNotBlank() }?.let { consume("phone", it.trim(), 600) }
        request.email?.takeIf { it.isNotBlank() }?.let { consume("email", it.trim().lowercase(Locale.ROOT), 600) }
    }

    private fun consume(kind: String, value: String, windowSeconds: Int) {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val allowed = redisTemplate.execute(
            SCRIPT, listOf("iam:login-id-lookup:$kind:$digest"), "5", windowSeconds.toString(),
        ) ?: throw ServiceException(AuthResult.LOGIN_ID_LOOKUP_RATE_LIMIT_UNAVAILABLE)
        if (allowed == 0L) throw BusinessException(AuthResult.LOGIN_ID_LOOKUP_RATE_LIMITED)
    }

    companion object {
        // 거절한 요청은 만료 시간을 연장하지 않아 지속 호출로 사용자를 무기한 잠그지 않는다.
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
