package com.media.bus.reservation.contract.service

import com.media.bus.reservation.contract.dto.request.CreateContractRequest
import com.media.bus.reservation.contract.dto.response.MemberInfo
import com.media.bus.reservation.contract.entity.ContractDetailEntity
import com.media.bus.reservation.contract.entity.ContractEntity
import com.media.bus.reservation.reservation.dto.response.StopInfo
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * ## 계약 도메인의 DB 저장을 담당하는 서비스
 *
 * 설계 의도:
 * - S2S 외부 호출은 `ContractFacade`에서 처리하고, 이 클래스는 트랜잭션 내 DB 작업만 담당한다.
 * - `ContractEntity` 저장 후 `ContractDetailEntity`를 저장하여 FK 참조 무결성을 보장한다.
 * - Exposed DAO `new()` 호출 시 트랜잭션 커밋 시점에 자동으로 INSERT된다.
 */
@Service
class ContractService {
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
}
