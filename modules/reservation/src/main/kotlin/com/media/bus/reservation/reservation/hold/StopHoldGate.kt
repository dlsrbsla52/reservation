package com.media.bus.reservation.reservation.hold

import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.stereotype.Component
import java.util.*

/**
 * ## 선점 요청 앞단의 Redis 게이트
 *
 * 인기 정류소가 풀리는 순간처럼 같은 광고면에 요청이 몰릴 때, 1건만 DB 로 들여보내고
 * 나머지는 Redis 왕복 한 번으로 돌려보낸다. 패자가 S2S 호출과 DB 커넥션을 쓰지 않게 하는 것이 목적이다.
 *
 * **이 게이트는 정합성을 보장하지 않는다.** TTL 이 트랜잭션보다 먼저 끝나거나 failover 로 키가 사라지면
 * 2건이 통과할 수 있고, 그때 승자를 1건으로 확정하는 것은 DB 의 `uq_reservation_active_slot` 이다.
 * 그래서 Redis 장애 시에는 막지 않고 통과시킨다(fail-open).
 *
 * 키 하나가 두 단계로 쓰인다.
 * 1. 진입: `SET NX PX gateTtl` — 예약 처리 중 락
 * 2. 성공 확정: TTL 을 `occupiedCacheTtl` 로 늘려 "점유됨" 캐시로 전환. 이 동안의 재요청은 DB 를 거치지 않고 거절된다.
 *    TTL 이 지나면 요청이 DB 로 내려가 다시 판정받으므로, 키 삭제가 누락돼도 광고면이 계속 막히지 않는다.
 */
@Component
class StopHoldGate(
    private val redisTemplate: StringRedisTemplate,
    private val properties: HoldProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 게이트 통과증.
     *
     * @property token 락 소유 토큰. Redis 장애로 게이트 없이 통과한 경우 null 이며, 이후 연산은 아무것도 하지 않는다.
     */
    data class Pass(val key: String, val token: String?)

    companion object {
        private const val KEY_PREFIX = "reservation:hold:"

        /** 내가 잡은 키일 때만 TTL 을 늘린다 — 내 락이 만료된 뒤 다른 요청이 잡은 키를 건드리지 않기 위함. */
        private val CONFIRM_SCRIPT: RedisScript<Long> = RedisScript.of(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('pexpire', KEYS[1], ARGV[2]) else return 0 end",
            Long::class.java,
        )

        /** 내가 잡은 키일 때만 지운다. */
        private val LEAVE_SCRIPT: RedisScript<Long> = RedisScript.of(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long::class.java,
        )

        fun keyOf(stopId: UUID, slotNo: Short): String = "$KEY_PREFIX$stopId:$slotNo"
    }

    /**
     * 게이트 진입을 시도한다. 기다리지 않는다 — 선착순이라 못 잡은 요청은 이미 진 것이다.
     *
     * @return 통과증. 다른 요청이 처리 중이거나 이미 점유된 광고면이면 null.
     */
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
