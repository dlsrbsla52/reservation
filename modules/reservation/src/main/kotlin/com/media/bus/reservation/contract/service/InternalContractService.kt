package com.media.bus.reservation.contract.service

import com.media.bus.common.exceptions.BusinessException
import com.media.bus.common.web.wrapper.PageResult
import com.media.bus.reservation.contract.dto.request.AdminContractSearchCondition
import com.media.bus.reservation.contract.dto.response.AdminContractView
import com.media.bus.reservation.contract.repository.ContractDetailRepository
import com.media.bus.reservation.contract.repository.ContractRepository
import com.media.bus.reservation.contract.result.ContractResult
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

/**
 * ## 관리자 전용 계약 조회 서비스(트랜잭션 경계)
 *
 * 설계 의도:
 * - 일반 사용자 흐름(`ContractService.getMyContracts`)이 본인 검증을 포함하는 것과 달리,
 *   이 서비스는 호출 측(iam 어드민)이 이미 권한 검증을 완료했다고 전제한다.
 * - Contract 페이지 조회 → 같은 페이지 내 ContractDetail을 bulk 조회로 결합하여 N+1을 피한다.
 * - 정류소 정보 결합은 트랜잭션 외부(`InternalContractFacade`)에서 처리한다 — DB와 S2S 호출의 경계를 분리.
 */
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

    /**
     * 매니저(memberId) 기준 계약 목록과 상세를 함께 조회한다.
     *
     * @param memberId 조회 대상 매니저 회원 ID
     * @param page     0-base 페이지 번호
     * @param size     페이지 크기
     * @return 정류소 정보는 비어있는(later enriched) 어드민 view 페이지
     */
    @Transactional(readOnly = true)
    fun getContractsByMemberId(memberId: UUID, page: Int, size: Int): PageResult<AdminContractView> {
        val contracts = contractRepository.findByMemberIdPaged(memberId, page, size)
        val totalCnt = contractRepository.countByMemberId(memberId)

        // ContractDetail은 1:1 관계지만 bulk 조회를 강제하여 향후 N+1 회귀를 예방한다.
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

    /** 소유자 제한 없이 계약과 계약 상세를 조회한다. 접근 제어는 internal S2S 경계에서 수행한다. */
    @Transactional(readOnly = true)
    fun getContractDetail(contractId: UUID): AdminContractView {
        val contract = contractRepository.findById(contractId)
            ?: throw BusinessException(ContractResult.CONTRACT_NOT_FOUND)
        val detail = contractDetailRepository.findByContractIds(listOf(contractId)).firstOrNull()
        return AdminContractView.from(contract, detail)
    }
}
