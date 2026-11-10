package com.media.bus.reservation.reservation.hold

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.data.redis.RedisConnectionFailureException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.data.redis.core.script.RedisScript
import java.time.Duration
import java.util.*

/**
 * 선점 게이트의 계약.
 * 게이트는 부하를 줄이는 장치일 뿐이라, Redis 가 죽었을 때 예약을 막아서는 안 된다(판정은 DB 제약이 한다).
 */
class StopHoldGateTest {

    private val valueOps = mockk<ValueOperations<String, String>>()
    private val redisTemplate = mockk<StringRedisTemplate>(relaxed = true).also {
        every { it.opsForValue() } returns valueOps
    }
    private val properties = HoldProperties(gateTtl = Duration.ofSeconds(5), occupiedCacheTtl = Duration.ofSeconds(30))
    private val gate = StopHoldGate(redisTemplate, properties)

    private val stopId = UUID.randomUUID()
    private val slotNo: Short = 1
    private val key = "reservation:hold:$stopId:1"

    @Test
    fun `키를 잡으면 광고면 단위 키와 소유 토큰이 담긴 통과증을 준다`() {
        every { valueOps.setIfAbsent(key, any(), Duration.ofSeconds(5)) } returns true

        val pass = gate.tryEnter(stopId, slotNo)

        assertThat(pass).isNotNull
        assertThat(pass!!.key).isEqualTo(key)
        assertThat(pass.token).isNotBlank()
    }

    @Test
    fun `이미 잡힌 키면 기다리지 않고 null 을 준다`() {
        every { valueOps.setIfAbsent(key, any(), any<Duration>()) } returns false

        assertThat(gate.tryEnter(stopId, slotNo)).isNull()
    }

    @Test
    fun `Redis 장애면 막지 않고 토큰 없는 통과증으로 통과시킨다`() {
        every { valueOps.setIfAbsent(any(), any(), any<Duration>()) } throws RedisConnectionFailureException("down")

        val pass = gate.tryEnter(stopId, slotNo)

        assertThat(pass).isNotNull
        assertThat(pass!!.token).isNull()
    }

    @Test
    fun `토큰 없는 통과증은 확정과 해제에서 Redis 를 호출하지 않는다`() {
        val pass = StopHoldGate.Pass(key, null)

        gate.confirm(pass)
        gate.leave(pass)

        verify(exactly = 0) { redisTemplate.execute(any<RedisScript<Long>>(), any<List<String>>(), *anyVararg()) }
    }

    @Test
    fun `확정은 내 토큰일 때만 점유 캐시 TTL 로 늘린다`() {
        gate.confirm(StopHoldGate.Pass(key, "my-token"))

        verify(exactly = 1) { redisTemplate.execute(any<RedisScript<Long>>(), listOf(key), "my-token", "30000") }
    }

    @Test
    fun `정리 중 Redis 가 실패해도 예외를 밖으로 내지 않는다`() {
        every { redisTemplate.delete(key) } throws RedisConnectionFailureException("down")

        gate.evict(SlotRef(stopId, slotNo))

        verify(exactly = 1) { redisTemplate.delete(key) }
    }
}
