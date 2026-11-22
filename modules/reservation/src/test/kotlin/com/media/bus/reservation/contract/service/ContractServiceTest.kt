package com.media.bus.reservation.contract.service

import com.media.bus.common.exceptions.BusinessException
import com.media.bus.reservation.contract.dto.request.CreateContractRequest
import com.media.bus.reservation.contract.entity.enums.PaymentCycle
import com.media.bus.reservation.contract.entity.enums.PaymentMethod
import com.media.bus.reservation.contract.repository.ContractRepository
import com.media.bus.reservation.contract.result.ContractResult
import com.media.bus.reservation.reservation.dto.response.StopInfo
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.*

/** ContractService 계약 생성 검증 테스트 */
class ContractServiceTest {
    private val contractRepository = mockk<ContractRepository>()
    private val contractService = ContractService(contractRepository)

    private val stopInfo = StopInfo(
        id = UUID.randomUUID(),
        stopId = "STOP-001",
        stopName = "강남역",
    )

    private fun request(
        start: OffsetDateTime,
        end: OffsetDateTime,
    ) = CreateContractRequest(
        stopId = stopInfo.id,
        contractName = "광역버스 정류소 광고 계약",
        totalAmount = BigDecimal("1200000.00"),
        payAmount = BigDecimal("100000.00"),
        paymentCycle = PaymentCycle.MONTHLY,
        paymentMethod = PaymentMethod.BANK_TRANSFER,
        memberId = null,
        note = null,
        contractStartDate = start,
        contractEndDate = end,
    )

    @Test
    fun `종료일이 시작일보다 빠르면 기간 검증 예외가 발생한다`() {
        val start = OffsetDateTime.parse("2026-07-02T00:00:00+09:00")
        val end = OffsetDateTime.parse("2026-07-01T00:00:00+09:00")

        val ex = assertThrows<BusinessException> {
            contractService.createContract(stopInfo, request(start, end))
        }
        assertEquals(ContractResult.CONTRACT_INVALID_PERIOD.code, ex.result.code)
    }

    @Test
    fun `종료일과 시작일이 같으면 기간 검증 예외가 발생한다`() {
        val same = OffsetDateTime.parse("2026-07-01T00:00:00+09:00")

        val ex = assertThrows<BusinessException> {
            contractService.createContract(stopInfo, request(same, same))
        }
        assertEquals(ContractResult.CONTRACT_INVALID_PERIOD.code, ex.result.code)
    }
}
