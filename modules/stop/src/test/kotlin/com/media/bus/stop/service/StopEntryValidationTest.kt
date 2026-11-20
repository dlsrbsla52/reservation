package com.media.bus.stop.service

import com.media.bus.common.exceptions.BusinessException
import com.media.bus.common.result.type.CommonResult
import com.media.bus.stop.dto.request.SimpleStopCreateRequest
import com.media.bus.stop.entity.enums.StopType
import com.media.bus.stop.guard.StopCommandGuard
import com.media.bus.stop.guard.StopPriceCommandGuard
import com.media.bus.stop.repository.StopPriceRepository
import com.media.bus.stop.repository.StopRepository
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.util.*

/** stop 진입점 입력 검증 테스트 */
@DisplayName("stop 진입점 입력 검증")
class StopEntryValidationTest {

    private val stopRepository = mockk<StopRepository>()
    private val stopPriceRepository = mockk<StopPriceRepository>()
    private val stopCommandGuard = mockk<StopCommandGuard>()
    private val stopPriceCommandGuard = mockk<StopPriceCommandGuard>()
    private val stopService = StopService(stopRepository, stopPriceRepository, stopCommandGuard)
    private val stopPriceService = StopPriceService(stopPriceRepository, stopPriceCommandGuard)

    @Test
    fun `일괄 조회는 빈 목록과 200개 초과를 저장소 조회 전에 거절한다`() {
        val empty = assertThrows<BusinessException> { stopService.getBusStopsByIds(emptyList()) }
        assertThat(empty.result).isEqualTo(CommonResult.VALIDATION_FAIL)

        assertThrows<BusinessException> { stopService.getBusStopsByIds(List(201) { UUID.randomUUID() }) }

        verify(exactly = 0) { stopRepository.findByIdIn(any()) }
    }

    @Test
    fun `단가가 0 이하면 정류소 존재 검사 전에 거절한다`() {
        val stopId = UUID.randomUUID()

        assertThrows<BusinessException> { stopPriceService.createPrice(stopId, BigDecimal.ZERO, null) }
        assertThrows<BusinessException> { stopPriceService.updatePrice(stopId, BigDecimal("-1"), null) }

        verify(exactly = 0) { stopPriceCommandGuard.validateStopExists(any()) }
        verify(exactly = 0) { stopPriceCommandGuard.validateExists(any()) }
    }

    @Test
    fun `단건 등록 요청이 길이 제약을 어기면 중복 검사 전에 거절한다`() {
        val request = SimpleStopCreateRequest(
            stopId = "1".repeat(51),
            stopName = "강남역",
            xCrd = "127.0276",
            yCrd = "37.4979",
            nodeId = "N1",
            stopsType = StopType.CENTER_LANE,
        )

        val ex = assertThrows<BusinessException> { stopService.createOneStop(UUID.randomUUID(), request) }

        assertThat(ex.result).isEqualTo(CommonResult.VALIDATION_FAIL)
        verify(exactly = 0) { stopCommandGuard.validateNotDuplicate(any()) }
    }
}
