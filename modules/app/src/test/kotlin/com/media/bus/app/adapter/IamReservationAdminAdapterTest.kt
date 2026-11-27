package com.media.bus.app.adapter

import com.media.bus.common.exceptions.BusinessException
import com.media.bus.common.exceptions.StorageException
import com.media.bus.common.result.type.CommonResult
import com.media.bus.common.web.wrapper.PageResult
import com.media.bus.iam.port.dto.reservation.CompleteToContractRequest
import com.media.bus.iam.port.dto.reservation.CreateContractRequest
import com.media.bus.reservation.contract.dto.request.AdminContractSearchCondition
import com.media.bus.reservation.contract.dto.response.AdminContractView
import com.media.bus.reservation.contract.entity.enums.ContractStatus
import com.media.bus.reservation.contract.entity.enums.PaymentCycle
import com.media.bus.reservation.contract.service.InternalContractFacade
import com.media.bus.reservation.reservation.entity.enums.ReservationStatus
import com.media.bus.reservation.reservation.result.ReservationResult
import com.media.bus.reservation.reservation.service.ReservationFacade
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.*

@DisplayName("IamReservationAdminAdapter")
class IamReservationAdminAdapterTest {

    private val contractFacade = mockk<InternalContractFacade>()
    private val reservationFacade = mockk<ReservationFacade>()
    private val adapter = IamReservationAdminAdapter(contractFacade, reservationFacade)
    private val now = OffsetDateTime.parse("2026-11-17T10:00:00+09:00")

    @Test
    fun `잘못된 계약 상태 문자열은 전체 조회로 바뀌지 않고 400 이다`() {
        val ex = assertThrows<BusinessException> {
            adapter.searchContracts(null, null, "NOT_A_STATUS", null, null, null, 0, 20)
        }

        assertThat(ex.result).isEqualTo(CommonResult.VALIDATION_FAIL)
        verify(exactly = 0) { contractFacade.searchContracts(any(), any(), any()) }
    }

    @Test
    fun `계약 검색은 상태를 enum 으로 넘기고 응답 enum 을 이름 문자열로 바꾼다`() {
        val condition = slot<AdminContractSearchCondition>()
        val view = AdminContractView(
            UUID.randomUUID(), UUID.randomUUID(), "1", "역", null, "계약", ContractStatus.ACTIVE, now, now,
            BigDecimal.TEN, null, PaymentCycle.MONTHLY, null, null, null, null, now,
        )
        every { contractFacade.searchContracts(capture(condition), 0, 20) } returns PageResult(listOf(view), 1, 20, 0)

        val page = adapter.searchContracts(null, null, "ACTIVE", null, null, null, 0, 20)

        assertThat(condition.captured.status).isEqualTo(ContractStatus.ACTIVE)
        assertThat(page.items!!.single().status).isEqualTo("ACTIVE")
        assertThat(page.items!!.single().paymentCycle).isEqualTo("MONTHLY")
        assertThat(page.items!!.single().paymentMethod).isNull()
    }

    @Test
    fun `예약 검색의 잘못된 일시 문자열은 400 이다`() {
        val ex = assertThrows<BusinessException> {
            adapter.searchAdminReservations(null, null, null, "2026-13-40", null, 0, 20)
        }
        assertThat(ex.result).isEqualTo(CommonResult.VALIDATION_FAIL)
    }

    @Test
    fun `예약 상태 변경은 문자열을 enum 으로 바꿔 Facade 에 넘긴다(선점 캐시 해제는 Facade 가 맡음)`() {
        val id = UUID.randomUUID()
        every { reservationFacade.updateReservationStatus(id, ReservationStatus.CANCELLED, "메모") } just runs

        adapter.updateReservationStatus(id, "CANCELLED", "메모")

        verify(exactly = 1) { reservationFacade.updateReservationStatus(id, ReservationStatus.CANCELLED, "메모") }
    }

    @Test
    fun `제공자의 404(StorageException)는 500 으로 바꾸지 않고 그대로 전달한다`() {
        val notFound = StorageException(ReservationResult.STOP_NOT_FOUND)
        every { contractFacade.createContract(any()) } throws notFound
        val request = CreateContractRequest(
            UUID.randomUUID(), "계약", BigDecimal.TEN, null, "MONTHLY", "CARD", null, null, now, now.plusMonths(1), null, null,
        )

        val ex = assertThrows<StorageException> { adapter.createContract(request) }

        assertThat(ex).isSameAs(notFound)
    }

    @Test
    fun `계약 전환에서 제공자의 404(StorageException)도 그대로 전달한다`() {
        val notFound = StorageException(ReservationResult.STOP_NOT_FOUND)
        every { reservationFacade.completeReservationToContract(any(), any()) } throws notFound
        val request = CompleteToContractRequest("계약", BigDecimal.TEN, null, "MONTHLY", "CASH", now, now, null)

        val ex = assertThrows<StorageException> { adapter.completeReservationToContract(UUID.randomUUID(), request) }

        assertThat(ex).isSameAs(notFound)
    }

    @Test
    fun `계약 전환의 잘못된 납부 주기는 400 이고 Facade 를 부르지 않는다`() {
        val request = CompleteToContractRequest("계약", BigDecimal.TEN, null, "WEEKLY", "BANK_TRANSFER", now, now, null)

        assertThrows<BusinessException> { adapter.completeReservationToContract(UUID.randomUUID(), request) }

        verify(exactly = 0) { reservationFacade.completeReservationToContract(any(), any()) }
    }
}
