package com.media.bus.content.scheduler

import com.media.bus.content.service.InquiryService
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.OffsetDateTime

class InquiryPurgeSchedulerTest {
    private val service = mockk<InquiryService>()
    private val scheduler = InquiryPurgeScheduler(service, Duration.ofDays(90))

    @Test
    fun `보관 기간만큼 거슬러 올라간 시각을 기준으로 파기를 요청한다`() {
        val cutoff = slot<OffsetDateTime>()
        every { service.purgeHandledBefore(capture(cutoff)) } returns 3

        scheduler.purgeExpiredInquiries()

        val expected = OffsetDateTime.now().minusDays(90)
        assertThat(Duration.between(cutoff.captured, expected).abs()).isLessThan(Duration.ofMinutes(1))
    }

    @Test
    fun `파기 중 예외가 나도 스케줄러 밖으로 던지지 않는다`() {
        every { service.purgeHandledBefore(any()) } throws IllegalStateException()

        scheduler.purgeExpiredInquiries()

        verify(exactly = 1) { service.purgeHandledBefore(any()) }
    }
}
