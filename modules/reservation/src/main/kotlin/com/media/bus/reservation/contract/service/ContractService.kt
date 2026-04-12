package com.media.bus.reservation.contract.service

import com.media.bus.common.exceptions.BusinessException
import com.media.bus.common.web.wrapper.PageResult
import com.media.bus.reservation.contract.dto.request.CreateContractRequest
import com.media.bus.reservation.contract.dto.response.ContractResponse
import com.media.bus.reservation.contract.dto.response.MemberInfo
import com.media.bus.reservation.contract.entity.ContractDetailEntity
import com.media.bus.reservation.contract.entity.ContractEntity
import com.media.bus.reservation.contract.repository.ContractRepository
import com.media.bus.reservation.contract.result.ContractResult
import com.media.bus.reservation.reservation.dto.response.StopInfo
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

/**
 * ## 계약 도메인의 DB 저장을 담당하는 서비스
 *
 * 설계 의도:
 * - S2S 외부 호출은 `ContractFacade`에서 처리하고, 이 클래스는 트랜잭션 내 DB 작업만 담당한다.
 * - `ContractEntity` 저장 후 `ContractDetailEntity`를 저장하여 FK 참조 무결성을 보장한다.
 * - Exposed DAO `new()` 호출 시 트랜잭션 커밋 시점에 자동으로 INSERT된다.
 */
@Service
class ContractService(
    private val contractRepository: ContractRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Contract와 ContractDetail을 함께 저장한다.
     * 단일 트랜잭션 내에서 실행되어 부분 저장을 방지한다.
     *
     * @param memberInfo IAM DB에서 재검증된 회원 정보
     * @param stopInfo   stop 서비스에서 확인된 정류소 정보
     * @param request    계약 생성 요청 DTO
     * @return 저장된 ContractEntity
     */
    @Transactional
    fun createContract(
        memberInfo: MemberInfo,
        stopInfo: StopInfo,
        request: CreateContractRequest,
    ): ContractEntity {
        val contract = ContractEntity.create(memberInfo, stopInfo, request)
        ContractDetailEntity.create(contract, request)

        log.debug(
            "[ContractService] 계약 저장 완료: contractId={}, memberId={}, stopId={}",
            contract.id.value, memberInfo.id, stopInfo.id,
        )
        return contract
    }

    /**
     * 본인 계약 목록 조회 (페이지네이션).
     *
     * 정류소 정보는 포함하지 않은 raw 응답을 반환한다 — 결합은 `ContractFacade`에서
     * 한 번의 bulk S2S 호출로 처리하여 N+1 문제를 방지한다.
     */
    @Transactional(readOnly = true)
    fun getMyContracts(memberId: UUID, page: Int, size: Int): PageResult<ContractResponse> {
        val contracts = contractRepository.findByMemberIdPaged(memberId, page, size)
        val totalCnt = contractRepository.countByMemberId(memberId)

        val items = contracts.map { ContractResponse.from(it) }
        return PageResult(
            items = items,
            totalCnt = totalCnt,
            pageRows = size,
            pageNum = page,
        )
    }

    /**
     * 본인 계약 단건 상세 조회.
     * 소유자가 아니면 `CONTRACT_ACCESS_DENIED`를 반환한다
     * (존재 여부 노출 방지를 위해 NOT_FOUND로 통합해도 무방하나, UI 메시지 분기를 위해 구분).
     */
    @Transactional(readOnly = true)
    fun getContractDetail(memberId: UUID, contractId: UUID): ContractResponse {
        val contract = contractRepository.findById(contractId)
            ?: throw BusinessException(ContractResult.CONTRACT_NOT_FOUND)

        if (contract.memberId != memberId) {
            throw BusinessException(ContractResult.CONTRACT_ACCESS_DENIED)
        }

        return ContractResponse.from(contract)
    }
}
