package com.media.bus.reservation.reservation.service

import com.media.bus.common.exceptions.BusinessException
import com.media.bus.common.result.type.CommonResult
import com.media.bus.reservation.contract.entity.enums.PaymentCycle
import com.media.bus.reservation.contract.entity.enums.PaymentMethod
import com.media.bus.reservation.reservation.dto.request.CompleteToContractRequest
import com.media.bus.reservation.reservation.entity.ReservationEntity
import com.media.bus.reservation.reservation.entity.enums.ReservationStatus
import com.media.bus.reservation.reservation.hold.StopHoldGate
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.*

/** ReservationFacade 관리자 진입점 입력 검증 테스트 */
@DisplayName("ReservationFacade 관리자 진입점 입력 검증")
class ReservationFacadeAdminValidationTest {

    private val stopResolutionService = mockk<StopResolutionService>()
    private val reservationService = mockk<ReservationService>()
    private val stopHoldGate = mockk<StopHoldGate>(relaxUnitFun = true)
    private val facade = ReservationFacade(stopResolutionService, reservationService, stopHoldGate)

    private fun completeRequest(contractName: String = "계약", payAmount: BigDecimal? = null, note: String? = null) =
        CompleteToContractRequest(
            contractName = contractName,
            totalAmount = BigDecimal("1000"),
            payAmount = payAmount,
            paymentCycle = PaymentCycle.MONTHLY,
            paymentMethod = PaymentMethod.BANK_TRANSFER,
            contractStartDate = OffsetDateTime.parse("2026-12-01T00:00:00+09:00"),
            contractEndDate = OffsetDateTime.parse("2027-11-30T00:00:00+09:00"),
            note = note,
        )

    @Test
    fun `예약 검색은 페이지와 크기를 허용 범위로 보정한다`() {
        every { reservationService.searchAdminReservations(null, null, null, null, null, 0, 100) } returns
            (emptyList<ReservationEntity>() to 0L)
        every { stopResolutionService.resolveStops(emptySet()) } returns emptyMap()

        val page = facade.searchAdminReservations(null, null, null, null, null, -3, 500)

        assertThat(page.pageNum).isEqualTo(0)
        assertThat(page.pageRows).isEqualTo(100)
        verify(exactly = 1) { reservationService.searchAdminReservations(null, null, null, null, null, 0, 100) }
    }

    @Test
    fun `상담 기록이 1000자를 넘으면 상태를 바꾸지 않고 거절한다`() {
        val ex = assertThrows<BusinessException> {
            facade.updateReservationStatus(UUID.randomUUID(), ReservationStatus.CONSULTING, "a".repeat(1001))
        }

        assertThat(ex.result).isEqualTo(CommonResult.VALIDATION_FAIL)
        verify(exactly = 0) { reservationService.updateReservationStatus(any(), any(), any()) }
    }

    @Test
    fun `계약 전환 요청이 DTO 제약을 어기면 예약 조회 전에 거절한다`() {
        assertThrows<BusinessException> { facade.completeReservationToContract(UUID.randomUUID(), completeRequest(contractName = "")) }
        assertThrows<BusinessException> {
            facade.completeReservationToContract(UUID.randomUUID(), completeRequest(payAmount = BigDecimal("-1")))
        }
        assertThrows<BusinessException> {
            facade.completeReservationToContract(UUID.randomUUID(), completeRequest(note = "a".repeat(1001)))
        }

        verify(exactly = 0) { reservationService.getReservationDetailForAdmin(any()) }
    }
}
