package com.media.bus.reservation.contract.service

import com.media.bus.common.exceptions.BusinessException
import com.media.bus.common.validation.InputValidator
import com.media.bus.common.web.wrapper.PageResult
import com.media.bus.reservation.contract.dto.request.AdminContractSearchCondition
import com.media.bus.reservation.contract.dto.request.CreateContractRequest
import com.media.bus.reservation.contract.dto.response.AdminContractView
import com.media.bus.reservation.contract.dto.response.ContractResponse
import com.media.bus.reservation.contract.result.ContractResult
import com.media.bus.reservation.reservation.service.StopResolutionService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.*

/** 관리자 전용 계약 조회 Facade */
@Service
class InternalContractFacade(
    private val internalContractService: InternalContractService,
    private val memberVerificationService: MemberVerificationService,
    private val stopResolutionService: StopResolutionService,
    private val contractService: ContractService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 검색된 계약 페이지에 정류소 정보를 bulk 결합한다. */
    fun searchContracts(
        condition: AdminContractSearchCondition,
        page: Int,
        size: Int,
    ): PageResult<AdminContractView> {
        validateSearchCondition(condition)
        return enrichStops(internalContractService.searchContracts(condition, safePage(page), safeSize(size)))
    }

    /** 회원(iam)과 정류소(stop)를 포트로 검증한 뒤 계약을 단일 트랜잭션으로 저장한다. */
    fun createContract(request: CreateContractRequest): ContractResponse {
        InputValidator.validate(request)
        memberVerificationService.verifyMemberIfPresent(request.memberId)
        val stopInfo = stopResolutionService.resolveStop(request.stopId)
        val contract = contractService.createContract(stopInfo, request)
        return ContractResponse.from(contract, stopNumber = stopInfo.stopId, stopName = stopInfo.stopName)
    }

    /** 매니저(memberId) 기준 계약 목록을 정류소 정보와 결합하여 반환한다. */
    fun getContractsByMemberId(memberId: UUID, page: Int, size: Int): PageResult<AdminContractView> {
        val raw = internalContractService.getContractsByMemberId(memberId, safePage(page), safeSize(size))
        return enrichStops(raw, memberId)
    }

    private fun enrichStops(
        raw: PageResult<AdminContractView>,
        memberId: UUID? = null,
    ): PageResult<AdminContractView> {
        val stopIds = raw.items.map { it.stopId }.toSet()
        val stopMap = stopResolutionService.resolveStops(stopIds)
        val enriched = raw.items.map { row ->
            val info = stopMap[row.stopId]
            row.copy(stopNumber = info?.stopId, stopName = info?.stopName)
        }
        log.debug(
            "[InternalContractFacade] 정류소 정보 결합 완료: memberId={}, stopCnt={}, resolved={}",
            memberId, stopIds.size, stopMap.size,
        )
        return PageResult(
            items = enriched,
            totalCnt = raw.totalCnt,
            pageRows = raw.pageRows,
            pageNum = raw.pageNum,
        )
    }

    /** 계약 상세에 정류소 정보를 결합하여 반환한다. */
    fun getContractDetail(contractId: UUID): AdminContractView {
        val raw = internalContractService.getContractDetail(contractId)
        val info = stopResolutionService.resolveStops(setOf(raw.stopId))[raw.stopId]
        return raw.copy(stopNumber = info?.stopId, stopName = info?.stopName)
    }

    private fun validateSearchCondition(condition: AdminContractSearchCondition) {
        val keyword = condition.keyword
        if (keyword != null && keyword.length > MAX_KEYWORD_LENGTH) {
            throw BusinessException(ContractResult.CONTRACT_KEYWORD_TOO_LONG)
        }
        val from = condition.contractStartFrom
        val to = condition.contractStartTo
        if (from != null && to != null && from > to) {
            throw BusinessException(ContractResult.CONTRACT_SEARCH_INVALID_PERIOD)
        }
    }

    private fun safePage(page: Int): Int = page.coerceAtLeast(0)

    private fun safeSize(size: Int): Int = size.coerceIn(1, MAX_PAGE_SIZE)

    companion object {
        const val MAX_PAGE_SIZE = 100
        const val MAX_KEYWORD_LENGTH = 100
    }
}
