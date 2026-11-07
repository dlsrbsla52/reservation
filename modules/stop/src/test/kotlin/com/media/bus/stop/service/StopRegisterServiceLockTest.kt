package com.media.bus.stop.service

import com.media.bus.common.exceptions.BusinessException
import com.media.bus.stop.client.SeoulBusApiClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.http.HttpStatus

/**
 * 정류소 일괄 등록의 **동시 실행 방지** 계약.
 * 같은 작업이 겹치면 같은 stopId 를 동시에 insert/update 하므로, 락을 못 잡으면 작업을 시작하지 않아야 한다.
 */
class StopRegisterServiceLockTest {

    private val valueOps = mockk<ValueOperations<String, String>>()
    private val redisTemplate = mockk<StringRedisTemplate>(relaxed = true).also {
        every { it.opsForValue() } returns valueOps
    }
    private val seoulBusApiClient = mockk<SeoulBusApiClient>()
    private val service = StopRegisterService(seoulBusApiClient, mockk(relaxed = true), redisTemplate)

    @Test
    fun `이미 실행 중이면 409 를 던지고 공공 API 를 호출하지 않는다`() {
        every { valueOps.setIfAbsent(any(), any(), any<java.time.Duration>()) } returns false

        assertThatThrownBy { service.registerAllFromPublicApi() }
            .isInstanceOf(BusinessException::class.java)
            .extracting("httpStatus").isEqualTo(HttpStatus.CONFLICT)
        verify(exactly = 0) { seoulBusApiClient.fetchTotalCount() }
    }

    @Test
    fun `락을 잡으면 실행하고 끝나면 자기 락을 해제한다`() {
        every { valueOps.setIfAbsent(any(), any(), any<java.time.Duration>()) } returns true
        every { seoulBusApiClient.fetchTotalCount() } returns 0
        every { seoulBusApiClient.pageSize } returns 1000

        val result = service.registerAllFromPublicApi()

        assertThat(result.totalCount).isZero()
        verify { redisTemplate.execute(any<org.springframework.data.redis.core.script.RedisScript<Long>>(), listOf("stop:bulk-register:lock"), any()) }
    }

    @Test
    fun `작업이 실패해도 락을 해제한다`() {
        every { valueOps.setIfAbsent(any(), any(), any<java.time.Duration>()) } returns true
        every { seoulBusApiClient.fetchTotalCount() } throws IllegalStateException("공공 API 장애")

        assertThatThrownBy { service.registerAllFromPublicApi() }.isInstanceOf(IllegalStateException::class.java)
        verify { redisTemplate.execute(any<org.springframework.data.redis.core.script.RedisScript<Long>>(), listOf("stop:bulk-register:lock"), any()) }
    }
}
