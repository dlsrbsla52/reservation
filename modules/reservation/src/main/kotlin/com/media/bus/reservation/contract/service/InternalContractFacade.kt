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

/**
 * ## 관리자 전용 계약 조회 Facade
 *
 * 설계 의도:
 * - `ContractFacade`와 동일한 패턴으로 트랜잭션 경계와 다른 모듈 호출 경계를 분리한다.
 *   - 1단계(트랜잭션 내부): `InternalContractService` → DB에서 Contract + ContractDetail 조회
 *   - 2단계(트랜잭션 외부): `StopResolutionService` → stop 모듈 일괄 조회 후 정류소 정보 주입
 * - 본인 인증 흐름과 달리 매니저 ID를 외부에서 주입받는다. 호출 측인 iam 어드민 모듈이
 *   이미 ADMIN 권한(`@Authorize`) 검증을 마쳤음을 전제로 동작한다.
 *
 * 입력 검증:
 * - 검색 조건 검증, 페이지 보정, 계약 생성 DTO 제약 검증을 이 Facade가 맡는다. 예전에는
 *   `InternalContractController`에만 있어서, 모듈 간 직접 호출로 바꾸면 사라질 검증이었다.
 *
 * Fallback 정책:
 * - stop 서비스 장애 시 `StopResolutionService.resolveStops`가 빈 맵으로 fallback 하므로
 *   계약 목록 자체는 정상 반환되고, 누락된 stopId 행은 stopName/stopNumber가 null로 노출된다.
 */
@Service
class InternalContractFacade(
    private val internalContractService: InternalContractService,
    private val memberVerificationService: MemberVerificationService,
    private val stopResolutionService: StopResolutionService,
    private val contractService: ContractService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 검색된 계약 페이지에 정류소 정보를 bulk 결합한다.
     *
     * `page`는 0 이상, `size`는 1..[MAX_PAGE_SIZE]로 보정한다.
     *
     * @throws BusinessException `keyword`가 [MAX_KEYWORD_LENGTH]자를 넘거나 계약 시작일 범위가 역전되면 400
     */
    fun searchContracts(
        condition: AdminContractSearchCondition,
        page: Int,
        size: Int,
    ): PageResult<AdminContractView> {
        validateSearchCondition(condition)
        return enrichStops(internalContractService.searchContracts(condition, safePage(page), safeSize(size)))
    }

    /**
     * 회원(iam)과 정류소(stop)를 포트로 검증한 뒤 계약을 단일 트랜잭션으로 저장한다.
     *
     * @throws BusinessException `request`가 DTO 제약(빈 계약명, 0 이하 금액 등)을 어기면 400 `VALIDATION_FAIL`
     */
    fun createContract(request: CreateContractRequest): ContractResponse {
        InputValidator.validate(request)
        memberVerificationService.verifyMemberIfPresent(request.memberId)
        val stopInfo = stopResolutionService.resolveStop(request.stopId)
        val contract = contractService.createContract(stopInfo, request)
        return ContractResponse.from(contract, stopNumber = stopInfo.stopId, stopName = stopInfo.stopName)
    }

    /**
     * 매니저(memberId) 기준 계약 목록을 정류소 정보와 결합하여 반환한다.
     *
     * @param memberId 조회 대상 매니저 회원 ID
     * @param page     0-base 페이지 번호. 음수면 0으로 보정
     * @param size     페이지 크기. 1..[MAX_PAGE_SIZE]로 보정
     */
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
