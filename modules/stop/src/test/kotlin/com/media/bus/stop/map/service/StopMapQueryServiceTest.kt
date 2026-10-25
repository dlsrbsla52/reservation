package com.media.bus.stop.map.service

import com.media.bus.stop.entity.enums.StopType
import com.media.bus.stop.map.dto.StopBoundsCriteria
import com.media.bus.stop.map.dto.StopMarkerResponse
import com.media.bus.stop.map.repository.StopMapRepository
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.math.BigDecimal
import java.util.*

/**
 * `StopMapQueryService` 단위 테스트.
 * Repository 가 `limit + 1` 건을 돌려준다는 계약 위에서 잘림 판정만 검증한다.
 */
@ExtendWith(MockKExtension::class)
class StopMapQueryServiceTest {

    @MockK
    lateinit var stopMapRepository: StopMapRepository

    @InjectMockKs
    lateinit var stopMapQueryService: StopMapQueryService

    @Test
    fun `상한_이하면_truncated는_false다`() {
        val criteria = criteria(limit = 3)
        every { stopMapRepository.findMarkersInBounds(criteria) } returns markers(3)

        val result = stopMapQueryService.findMarkersInBounds(criteria)

        assertThat(result.items).hasSize(3)
        assertThat(result.truncated).isFalse()
        assertThat(result.limit).isEqualTo(3)
    }

    @Test
    fun `limit을_초과해_오면_잘라내고_truncated를_true로_설정한다`() {
        val criteria = criteria(limit = 3)
        // Repository 는 잘림 판정용으로 한 건을 더 읽어온다
        every { stopMapRepository.findMarkersInBounds(criteria) } returns markers(4)

        val result = stopMapQueryService.findMarkersInBounds(criteria)

        assertThat(result.items).hasSize(3)
        assertThat(result.truncated).isTrue()
    }

    @Test
    fun `결과가_없으면_빈_목록과_false를_반환한다`() {
        val criteria = criteria(limit = 10)
        every { stopMapRepository.findMarkersInBounds(criteria) } returns emptyList()

        val result = stopMapQueryService.findMarkersInBounds(criteria)

        assertThat(result.items).isEmpty()
        assertThat(result.truncated).isFalse()
    }

    private fun criteria(limit: Int) = StopBoundsCriteria(
        swLat = BigDecimal("37.4000000"),
        swLng = BigDecimal("126.8000000"),
        neLat = BigDecimal("37.7000000"),
        neLng = BigDecimal("127.2000000"),
        limit = limit,
    )

    private fun markers(count: Int): List<StopMarkerResponse> = (1..count).map {
        StopMarkerResponse(
            id = UUID.randomUUID(),
            stopId = "1000$it",
            stopName = "정류소$it",
            lat = BigDecimal("37.500000$it"),
            lng = BigDecimal("127.000000$it"),
            stopsType = StopType.VILLAGE_BUS,
        )
    }
}
