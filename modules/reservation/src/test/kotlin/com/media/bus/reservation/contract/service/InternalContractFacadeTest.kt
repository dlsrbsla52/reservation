package com.media.bus.reservation.contract.service

import com.media.bus.common.exceptions.BusinessException
import com.media.bus.common.result.type.CommonResult
import com.media.bus.common.web.wrapper.PageResult
import com.media.bus.reservation.contract.dto.request.AdminContractSearchCondition
import com.media.bus.reservation.contract.dto.request.CreateContractRequest
import com.media.bus.reservation.contract.entity.enums.PaymentCycle
import com.media.bus.reservation.contract.entity.enums.PaymentMethod
import com.media.bus.reservation.reservation.service.StopResolutionService
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

/**
 * ## InternalContractFacade 입력 검증 테스트
 *
 * 예전에는 `InternalContractController`에만 있던 검증·보정을 Facade로 옮겼다.
 * 모듈 간 직접 호출로 바뀌어도 같은 규칙이 실행되는지 확인한다.
 */
@DisplayName("InternalContractFacade 입력 검증")
class InternalContractFacadeTest {

    private val internalContractService = mockk<InternalContractService>()
    private val memberVerificationService = mockk<MemberVerificationService>()
    private val stopResolutionService = mockk<StopResolutionService>()
    private val contractService = mockk<ContractService>()
    private val facade = InternalContractFacade(
        internalContractService, memberVerificationService, stopResolutionService, contractService,
    )

    private fun emptyCondition(
        keyword: String? = null,
        from: OffsetDateTime? = null,
        to: OffsetDateTime? = null,
    ) = AdminContractSearchCondition(null, null, null, keyword, from, to)

    private fun createRequest(contractName: String = "계약", totalAmount: BigDecimal = BigDecimal("1000")) =
        CreateContractRequest(
            stopId = UUID.randomUUID(),
            contractName = contractName,
            totalAmount = totalAmount,
            payAmount = null,
            paymentCycle = PaymentCycle.MONTHLY,
            paymentMethod = PaymentMethod.BANK_TRANSFER,
            memberId = null,
            note = null,
            contractStartDate = OffsetDateTime.parse("2026-12-01T00:00:00+09:00"),
            contractEndDate = OffsetDateTime.parse("2027-11-30T00:00:00+09:00"),
        )

    @Test
    fun `회원 계약 조회는 페이지와 크기를 허용 범위로 보정한다`() {
        val memberId = UUID.randomUUID()
        every { internalContractService.getContractsByMemberId(memberId, 0, 100) } returns
            PageResult(emptyList(), 0L, 100, 0)
        every { stopResolutionService.resolveStops(emptySet()) } returns emptyMap()

        facade.getContractsByMemberId(memberId, -1, 101)

        verify(exactly = 1) { internalContractService.getContractsByMemberId(memberId, 0, 100) }
    }

    @Test
    fun `계약 검색은 크기 0을 1로 보정한다`() {
        val condition = emptyCondition()
        every { internalContractService.searchContracts(condition, 0, 1) } returns PageResult(emptyList(), 0L, 1, 0)
        every { stopResolutionService.resolveStops(emptySet()) } returns emptyMap()

        facade.searchContracts(condition, 0, 0)

        verify(exactly = 1) { internalContractService.searchContracts(condition, 0, 1) }
    }

    @Test
    fun `계약 시작일 검색 범위가 역전되면 검증 예외가 발생한다`() {
        val from = OffsetDateTime.parse("2026-07-02T00:00:00+09:00")
        val to = OffsetDateTime.parse("2026-07-01T00:00:00+09:00")

        val ex = assertThrows<BusinessException> { facade.searchContracts(emptyCondition(from = from, to = to), 0, 20) }

        assertThat(ex.result).isEqualTo(CommonResult.VALIDATION_FAIL)
        verify(exactly = 0) { internalContractService.searchContracts(any(), any(), any()) }
    }

    @Test
    fun `검색어가 100자를 넘으면 검증 예외가 발생한다`() {
        assertThrows<BusinessException> { facade.searchContracts(emptyCondition(keyword = "a".repeat(101)), 0, 20) }
    }

    @Test
    fun `빈 계약명이나 0 이하 금액이면 회원·정류소 조회 전에 거절한다`() {
        assertThrows<BusinessException> { facade.createContract(createRequest(contractName = " ")) }
        assertThrows<BusinessException> { facade.createContract(createRequest(totalAmount = BigDecimal.ZERO)) }
        assertThrows<BusinessException> { facade.createContract(createRequest(contractName = "a".repeat(301))) }

        verify(exactly = 0) { memberVerificationService.verifyMemberIfPresent(any()) }
        verify(exactly = 0) { stopResolutionService.resolveStop(any()) }
    }
}
