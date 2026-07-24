package com.media.bus.reservation.contract.service

import com.media.bus.common.exceptions.BusinessException
import com.media.bus.reservation.contract.repository.ContractDetailRepository
import com.media.bus.reservation.contract.repository.ContractRepository
import com.media.bus.reservation.contract.result.ContractResult
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.*

/**
 * ## InternalContractService 어드민 계약 상세 조회 테스트
 *
 * 존재하지 않는 계약 ID로 상세 조회 시 `CONTRACT_NOT_FOUND`가 발생하는지 검증한다.
 * 계약이 없으면 상세(ContractDetail) 조회 이전에 차단되어야 한다.
 */
class InternalContractServiceTest {
    private val contractRepository = mockk<ContractRepository>()
    private val contractDetailRepository = mockk<ContractDetailRepository>()
    private val service = InternalContractService(contractRepository, contractDetailRepository)

    @Test
    fun `존재하지 않는 계약 상세 조회는 CONTRACT_NOT_FOUND 예외를 발생시킨다`() {
        val contractId = UUID.randomUUID()
        every { contractRepository.findById(contractId) } returns null

        val ex = assertThrows<BusinessException> {
            service.getContractDetail(contractId)
        }
        assertEquals(ContractResult.CONTRACT_NOT_FOUND.code, ex.result.code)
    }
}
