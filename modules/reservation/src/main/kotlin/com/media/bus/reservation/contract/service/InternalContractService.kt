package com.media.bus.reservation.contract.service

import com.media.bus.common.exceptions.BusinessException
import com.media.bus.common.web.wrapper.PageResult
import com.media.bus.reservation.contract.dto.request.AdminContractSearchCondition
import com.media.bus.reservation.contract.dto.response.AdminContractView
import com.media.bus.reservation.contract.entity.ContractEntity
import com.media.bus.reservation.contract.entity.enums.ContractStatus
import com.media.bus.reservation.contract.entity.enums.PaymentStatus
import com.media.bus.reservation.contract.repository.ContractDetailRepository
import com.media.bus.reservation.contract.repository.ContractRepository
import com.media.bus.reservation.contract.result.ContractResult
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.*

/** 관리자 전용 계약 조회 서비스(트랜잭션 경계) */
@Service
class InternalContractService(
    private val contractRepository: ContractRepository,
    private val contractDetailRepository: ContractDetailRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 전체 계약을 선택 조건으로 검색하고 페이지 내 상세를 bulk 결합한다. */
    @Transactional(readOnly = true)
    fun searchContracts(
        condition: AdminContractSearchCondition,
        page: Int,
        size: Int,
    ): PageResult<AdminContractView> {
        val contracts = contractRepository.searchAdminContracts(condition, page, size)
        val totalCnt = contractRepository.countAdminContracts(condition)
        val detailMap = contractDetailRepository
            .findByContractIds(contracts.map { it.id.value })
            .associateBy { it.contractId }
        val items = contracts.map { AdminContractView.from(it, detailMap[it.id.value]) }
        return PageResult(items = items, totalCnt = totalCnt, pageRows = size, pageNum = page)
    }

    /** 매니저(memberId) 기준 계약 목록과 상세를 함께 조회한다. */
    @Transactional(readOnly = true)
    fun getContractsByMemberId(memberId: UUID, page: Int, size: Int): PageResult<AdminContractView> {
        val contracts = contractRepository.findByMemberIdPaged(memberId, page, size)
        val totalCnt = contractRepository.countByMemberId(memberId)

        val detailMap = contractDetailRepository
            .findByContractIds(contracts.map { it.id.value })
            .associateBy { it.contractId }

        val items = contracts.map { AdminContractView.from(it, detailMap[it.id.value]) }
        log.debug(
            "[InternalContractService] 어드민 계약 조회: memberId={}, page={}, size={}, count={}, total={}",
            memberId, page, size, items.size, totalCnt,
        )
        return PageResult(
            items = items,
            totalCnt = totalCnt,
            pageRows = size,
            pageNum = page,
        )
    }

    @Transactional
    fun updateContractStatus(contractId: UUID, requiredManagerId: UUID?, status: ContractStatus, cancelReason: String?) {
        val contract = findManagedContractForUpdate(contractId, requiredManagerId)
        val previous = contract.status
        if (previous == status) return
        if (!previous.canTransitionTo(status)) {
            throw BusinessException(ContractResult.CONTRACT_INVALID_STATE_TRANSITION)
        }
        if (status == ContractStatus.CANCELLED) {
            contract.cancel(OffsetDateTime.now(), cancelReason)
        } else {
            contract.status = status
        }
    }

    @Transactional
    fun updateContractPaymentStatus(
        contractId: UUID,
        requiredManagerId: UUID?,
        status: PaymentStatus,
        paidAmount: BigDecimal?,
        paidAt: OffsetDateTime?,
    ) {
        findManagedContractForUpdate(contractId, requiredManagerId)
        val detail = contractDetailRepository.findByContractIds(listOf(contractId)).firstOrNull()
            ?: throw BusinessException(ContractResult.CONTRACT_DETAIL_NOT_FOUND)

        if (status == PaymentStatus.PAID) {
            detail.paidAmount = paidAmount ?: detail.paidAmount ?: detail.totalAmount
            detail.paidAt = paidAt ?: detail.paidAt ?: OffsetDateTime.now()
        } else {
            detail.paidAmount = null
            detail.paidAt = null
        }
        detail.paymentStatus = status
    }

    private fun findManagedContractForUpdate(contractId: UUID, requiredManagerId: UUID?): ContractEntity {
        val contract = contractRepository.findByIdForUpdate(contractId)
            ?: throw BusinessException(ContractResult.CONTRACT_NOT_FOUND)
        if (requiredManagerId != null && contract.managerId != requiredManagerId) {
            throw BusinessException(ContractResult.CONTRACT_ACCESS_DENIED)
        }
        return contract
    }

    /** 소유자 제한 없이 계약과 계약 상세를 조회한다. 접근 제어는 호출 경로가 맡는다(iam 관리자 API의 `@Authorize`와 app 인증 필터의 관리자 발급처 검사). */
    @Transactional(readOnly = true)
    fun getContractDetail(contractId: UUID): AdminContractView {
        val contract = contractRepository.findById(contractId)
            ?: throw BusinessException(ContractResult.CONTRACT_NOT_FOUND)
        val detail = contractDetailRepository.findByContractIds(listOf(contractId)).firstOrNull()
        return AdminContractView.from(contract, detail)
    }
}
