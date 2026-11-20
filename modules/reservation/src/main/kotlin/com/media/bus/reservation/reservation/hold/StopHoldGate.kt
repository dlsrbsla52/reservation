package com.media.bus.reservation.reservation.hold

import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.stereotype.Component
import java.util.*

/** 선점 요청 앞단의 Redis 게이트 */
@Component
class StopHoldGate(
    private val redisTemplate: StringRedisTemplate,
    private val properties: HoldProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 게이트 통과증. */
    data class Pass(val key: String, val token: String?)

    companion object {
        private const val KEY_PREFIX = "reservation:hold:"

        private val CONFIRM_SCRIPT: RedisScript<Long> = RedisScript.of(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('pexpire', KEYS[1], ARGV[2]) else return 0 end",
            Long::class.java,
        )

        private val LEAVE_SCRIPT: RedisScript<Long> = RedisScript.of(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long::class.java,
        )

        fun keyOf(stopId: UUID, slotNo: Short): String = "$KEY_PREFIX$stopId:$slotNo"
    }

    /** 게이트 진입을 시도한다. */
    fun tryEnter(stopId: UUID, slotNo: Short): Pass? {
        val key = keyOf(stopId, slotNo)
        val token = UUID.randomUUID().toString()
        return try {
            val acquired = redisTemplate.opsForValue().setIfAbsent(key, token, properties.gateTtl) == true
            if (acquired) Pass(key, token) else null
        } catch (e: Exception) {
            log.warn("[StopHoldGate] Redis 장애로 게이트 없이 통과시킨다. DB 제약이 판정한다. key={}, cause={}", key, e.message)
            Pass(key, null)
        }
    }

    /** 예약이 커밋된 뒤 호출한다. 처리 중 락을 "점유됨" 캐시로 바꾼다. */
    fun confirm(pass: Pass) {
        val token = pass.token ?: return
        runQuietly("confirm", pass.key) {
            redisTemplate.execute(CONFIRM_SCRIPT, listOf(pass.key), token, properties.occupiedCacheTtl.toMillis().toString())
        }
    }

    /** 예약이 실패했을 때 호출한다. 내 락을 풀어 다음 요청이 바로 시도할 수 있게 한다. */
    fun leave(pass: Pass) {
        val token = pass.token ?: return
        runQuietly("leave", pass.key) {
            redisTemplate.execute(LEAVE_SCRIPT, listOf(pass.key), token)
        }
    }

    /** 선점이 풀렸을 때(취소·반려·만료·완료) 커밋 후 호출한다. 점유 캐시를 지워 새 예약이 DB 까지 가게 한다. */
    fun evict(slot: SlotRef) {
        val key = keyOf(slot.stopId, slot.slotNo)
        runQuietly("evict", key) { redisTemplate.delete(key) }
    }

    /** 게이트 정리는 실패해도 예약 결과를 뒤집지 않는다. 남은 키는 TTL 로 사라진다. */
    private fun runQuietly(operation: String, key: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            log.warn("[StopHoldGate] {} 실패 — TTL 만료로 정리된다. key={}, cause={}", operation, key, e.message)
        }
    }
}
