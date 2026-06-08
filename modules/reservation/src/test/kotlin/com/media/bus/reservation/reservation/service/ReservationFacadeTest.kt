package com.media.bus.reservation.reservation.service

import com.media.bus.common.web.wrapper.PageResult
import com.media.bus.reservation.reservation.dto.response.MyReservationResponse
import com.media.bus.reservation.reservation.dto.response.StopInfo
import com.media.bus.reservation.reservation.entity.enums.ReservationStatus
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.OffsetDateTime
import java.util.*

/**
 * ## ReservationFacade 단위 테스트
 *
 * 어드민 회원별 예약 조회(`getMemberReservations`)가 임의 `memberId` 로 예약을 조회하고
 * 정류소 정보를 결합하는지, stop 서비스 장애 시 graceful fallback 하는지 검증한다.
 */
@ExtendWith(MockKExtension::class)
@DisplayName("ReservationFacade 단위 테스트")
class ReservationFacadeTest {

    @MockK
    private lateinit var stopResolutionService: StopResolutionService

    @MockK
    private lateinit var reservationService: ReservationService

    @InjectMockKs
    private lateinit var reservationFacade: ReservationFacade

    private val memberId = UUID.randomUUID()
    private val stopId = UUID.randomUUID()

    /** stop 정보가 아직 결합되지 않은(null) 원시 예약 row */
    private fun rawReservation(): MyReservationResponse =
        MyReservationResponse(
            reservationId = UUID.randomUUID(),
            stopId = stopId,
            stopNumber = null,
            stopName = null,
            status = ReservationStatus.PENDING,
            consultationRequestedAt = OffsetDateTime.now(),
            desiredContractStartDate = null,
            createdAt = OffsetDateTime.now(),
        )

    @Test
    @DisplayName("회원별 예약을 memberId 로 조회하고 정류소 정보를 결합한다")
    fun `정상 조회 및 정류소 결합`() {
        every { reservationService.getMyReservations(memberId, 0, 20) } returns
            PageResult(items = listOf(rawReservation()), totalCnt = 1, pageRows = 20, pageNum = 0)
        every { stopResolutionService.resolveStops(setOf(stopId)) } returns
            mapOf(stopId to StopInfo(id = stopId, stopId = "STOP-001", stopName = "강남역"))

        val result = reservationFacade.getMemberReservations(memberId, 0, 20)

        assertThat(result.items).hasSize(1)
        assertThat(result.items[0].stopNumber).isEqualTo("STOP-001")
        assertThat(result.items[0].stopName).isEqualTo("강남역")
        assertThat(result.totalCnt).isEqualTo(1L)
        // 본인이 아닌 대상 memberId 로 조회했는지 확인
        verify { reservationService.getMyReservations(memberId, 0, 20) }
    }

    @Test
    @DisplayName("stop 서비스 장애(빈 맵)면 stopNumber/stopName 은 null 로 fallback 된다")
    fun `정류소 조회 실패 fallback`() {
        every { reservationService.getMyReservations(memberId, 0, 20) } returns
            PageResult(items = listOf(rawReservation()), totalCnt = 1, pageRows = 20, pageNum = 0)
        every { stopResolutionService.resolveStops(setOf(stopId)) } returns emptyMap()

        val result = reservationFacade.getMemberReservations(memberId, 0, 20)

        assertThat(result.items).hasSize(1)
        assertThat(result.items[0].stopNumber).isNull()
        assertThat(result.items[0].stopName).isNull()
    }
}
