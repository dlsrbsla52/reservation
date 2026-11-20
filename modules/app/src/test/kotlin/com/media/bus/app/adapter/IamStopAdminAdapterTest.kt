package com.media.bus.app.adapter

import com.media.bus.common.exceptions.BusinessException
import com.media.bus.common.exceptions.ServiceException
import com.media.bus.common.result.type.CommonResult
import com.media.bus.iam.port.dto.stop.SimpleStopCreateRequest
import com.media.bus.stop.dto.request.StopSearchCriteria
import com.media.bus.stop.dto.response.BusStopResponse
import com.media.bus.stop.dto.response.StopBulkRegisterResult
import com.media.bus.stop.entity.enums.StopType
import com.media.bus.stop.map.dto.StopBoundsResponse
import com.media.bus.stop.map.dto.StopMarkerResponse
import com.media.bus.stop.map.service.StopMapQueryService
import com.media.bus.stop.result.StopResult
import com.media.bus.stop.service.StopPriceService
import com.media.bus.stop.service.StopRegisterService
import com.media.bus.stop.service.StopService
import io.github.resilience4j.bulkhead.Bulkhead
import io.github.resilience4j.bulkhead.BulkheadFullException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.*

/** iam → stop 어댑터 계약 테스트 */
@DisplayName("IamStopAdminAdapter")
class IamStopAdminAdapterTest {

    private val stopService = mockk<StopService>()
    private val stopPriceService = mockk<StopPriceService>()
    private val stopMapQueryService = mockk<StopMapQueryService>()
    private val stopRegisterService = mockk<StopRegisterService>()
    private val adapter = IamStopAdminAdapter(stopService, stopPriceService, stopMapQueryService, stopRegisterService)

    private val now = OffsetDateTime.parse("2026-11-17T10:00:00+09:00")
    private fun busStop(unitPrice: BigDecimal?) = BusStopResponse(
        id = UUID.randomUUID(), stopId = "12345", stopName = "강남역", xCrd = "127.0276000", yCrd = "37.4979000",
        nodeId = "N1", stopsType = StopType.CENTER_LANE.name, createdAt = now, updatedAt = now, unitPrice = unitPrice,
    )

    @Test
    fun `단가가 있는 정류소의 unitPrice 와 좌표가 변환 후에도 유지된다`() {
        val stop = busStop(BigDecimal("1500.00"))
        every { stopService.getBusStop(StopSearchCriteria.ByPk(setOf(stop.id))) } returns listOf(stop)

        val info = adapter.getStopByPk(stop.id).single()

        assertThat(info.unitPrice).isEqualByComparingTo("1500.00")
        assertThat(info.xCrd).isEqualTo("127.0276000")
        assertThat(info.yCrd).isEqualTo("37.4979000")
        assertThat(info.stopsType).isEqualTo("CENTER_LANE")
        assertThat(info.createdAt).isEqualTo(now)
    }

    @Test
    fun `지도 마커의 stopsType enum 은 예전 JSON 과 같은 이름 문자열이 된다`() {
        val marker = StopMarkerResponse(UUID.randomUUID(), "1", "역", BigDecimal("37.5"), BigDecimal("127.0"), StopType.VILLAGE_BUS)
        every { stopMapQueryService.findMarkersInBounds(any()) } returns StopBoundsResponse(listOf(marker), true, 1000)

        val result = adapter.getStopsInBounds("37.4", "126.9", "37.6", "127.1", null, null)

        assertThat(result.items!!.single().stopsType).isEqualTo("VILLAGE_BUS")
        assertThat(result.truncated).isTrue()
        assertThat(result.limit).isEqualTo(1000)
    }

    @Test
    fun `빈 일괄 조회는 제공자를 부르지 않는다`() {
        assertThat(adapter.getStopsByPks(emptyList())).isEmpty()
        verify(exactly = 0) { stopService.getBusStopsByIds(any()) }
    }

    @Test
    fun `알 수 없는 정류소 유형으로 단건 등록하면 400 이고 등록하지 않는다`() {
        val request = SimpleStopCreateRequest("1", "역", "127.0", "37.5", "N1", stopsType = "UNKNOWN")

        val ex = assertThrows<BusinessException> { adapter.createSimpleStop(request, UUID.randomUUID()) }

        assertThat(ex.result).isEqualTo(CommonResult.VALIDATION_FAIL)
        verify(exactly = 0) { stopService.createOneStop(any<UUID>(), any()) }
    }

    @Test
    fun `제공자 과부하는 429 BULKHEAD_FULL 로 바뀐다`() {
        every { stopRegisterService.registerAllFromPublicApi() } throws
            BulkheadFullException.createBulkheadFullException(Bulkhead.ofDefaults("orderDatabase"))

        val ex = assertThrows<BusinessException> { adapter.registerBulk() }

        assertThat(ex.result).isEqualTo(CommonResult.BULKHEAD_FULL)
        assertThat(ex.httpStatus).isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
    }

    @Test
    fun `서울 공공 API 실패(ServiceException)는 예전처럼 500 일반 오류로 바뀐다`() {
        val original = ServiceException(StopResult.SEOUL_API_PARSE_FAIL)
        every { stopRegisterService.registerAllFromPublicApi() } throws original

        val ex = assertThrows<ServiceException> { adapter.registerBulk() }

        assertThat(ex.result).isEqualTo(CommonResult.ERROR)
        assertThat(ex.message).isEqualTo(CommonResult.ERROR.message)
        assertThat(ex.cause).isSameAs(original)
    }

    @Test
    fun `제공자의 4xx 비즈니스 예외는 그대로 전달한다`() {
        val conflict = BusinessException(StopResult.BULK_REGISTER_IN_PROGRESS)
        every { stopRegisterService.registerAllFromPublicApi() } throws conflict

        val ex = assertThrows<BusinessException> { adapter.registerBulk() }

        assertThat(ex).isSameAs(conflict)
    }

    @Test
    fun `일괄 등록 결과의 집계 필드를 모두 옮긴다`() {
        every { stopRegisterService.registerAllFromPublicApi() } returns StopBulkRegisterResult(1, 2, 3, 4, 10)

        val result = adapter.registerBulk()

        assertThat(listOf(result.savedCount, result.updatedCount, result.skippedCount, result.failedCount, result.totalCount))
            .containsExactly(1, 2, 3, 4, 10)
    }

    @Test
    fun `제공자의 인증과 인가 오류는 동일한 예외로 전달한다`() {
        val unauthorized = com.media.bus.common.exceptions.NoAuthenticationException()
        val forbidden = com.media.bus.common.exceptions.NoAuthorizationException()
        every { stopRegisterService.registerAllFromPublicApi() } throws unauthorized
        assertThat(assertThrows<com.media.bus.common.exceptions.NoAuthenticationException> { adapter.registerBulk() })
            .isSameAs(unauthorized)
        every { stopRegisterService.registerAllFromPublicApi() } throws forbidden
        assertThat(assertThrows<com.media.bus.common.exceptions.NoAuthorizationException> { adapter.registerBulk() })
            .isSameAs(forbidden)
    }
}
