package com.media.bus.reservation.contract.service

import com.media.bus.contract.security.MemberPrincipal
import com.media.bus.reservation.contract.dto.request.CreateContractRequest
import com.media.bus.reservation.contract.dto.response.ContractResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * ## 계약 생성 흐름을 조율하는 Facade
 *
 * 설계 의도:
 * - `ReservationFacade`와 동일한 패턴으로 트랜잭션을 명시적으로 분리한다.
 * - S2S 호출(IAM 회원 재검증, Stop 정류소 확인)은 트랜잭션 외부에서 먼저 처리한다.
 * - DB 저장은 `ContractService`의 `@Transactional` 경계 내에서만 수행한다.
 *
 * 흐름:
 * 1단계: `IamServiceClient` → IAM DB 회원 재검증 (S2S, 트랜잭션 외부)
 * 2단계: `StopResolutionService` → 정류소 유효성 확인 (S2S, 트랜잭션 외부)
 * 3단계: `ContractService` → Contract + ContractDetail DB 저장 (`@Transactional`)
 */
@Service
class ContractFacade(
    private val memberVerificationService: MemberVerificationService,
    private val stopResolutionService: com.media.bus.reservation.reservation.service.StopResolutionService,
    private val contractService: ContractService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 계약을 생성한다.
     *
     * @param principal JWT 클레임에서 파싱된 인증 정보 (로깅/감사 용도)
     * @param jwt       Authorization 헤더에서 추출한 원본 JWT 문자열 (IAM 재검증 용도)
     * @param request   계약 생성 요청 DTO
     * @return 생성된 계약 응답 DTO
     */
    fun createContract(
        principal: MemberPrincipal,
        jwt: String,
        request: CreateContractRequest,
    ): ContractResponse {
        // 1단계: IAM DB에서 회원 재검증
        val memberInfo = memberVerificationService.verifyMember(jwt)
        log.debug("[ContractFacade] IAM 회원 재검증 완료: memberId={}", memberInfo.id)

        // 2단계: stop 서비스에서 정류소 존재 여부 확인
        val stopInfo = stopResolutionService.resolveStop(request.stopId)
        log.debug("[ContractFacade] 정류소 확인 완료: stopName={}, memberId={}", stopInfo.stopName, principal.id)

        // 3단계: Contract + ContractDetail DB 저장 (단일 트랜잭션)
        val contract = contractService.createContract(memberInfo, stopInfo, request)
        return ContractResponse.from(contract)
    }
}
