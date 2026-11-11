package com.media.bus.reservation.contract.service

import com.media.bus.common.web.wrapper.PageResult
import com.media.bus.contract.security.MemberPrincipal
import com.media.bus.reservation.contract.dto.response.ContractResponse
import com.media.bus.reservation.reservation.service.StopResolutionService
import org.springframework.stereotype.Service
import java.util.*

/**
 * ## 회원 본인 계약 조회를 조율하는 Facade
 *
 * 설계 의도:
 * - S2S 호출(Stop 정류소 정보 결합)은 트랜잭션 외부에서 처리하고,
 *   DB 조회는 `ContractService`의 `@Transactional` 경계 안에서만 한다.
 * - 계약 생성은 관리자 기능이므로 `InternalContractFacade`(iam admin → S2S)가 맡는다.
 */
@Service
class ContractFacade(
    private val stopResolutionService: StopResolutionService,
    private val contractService: ContractService,
) {
    /**
     * 본인 계약 목록 조회 + 정류소 정보 결합.
     *
     * `ReservationFacade.getMyReservations`와 동일한 패턴:
     * 1단계: DB에서 계약 목록 조회 (stop 정보는 null)
     * 2단계: 중복 제거된 stopId 집합에 대해 **1회 bulk S2S 조회** → N+1 방지
     * 3단계: 응답 row별 stopName/stopNumber 주입
     *
     * Stop 서비스 장애 시 `StopResolutionService.resolveStops`가 빈 맵으로 fallback 하므로
     * 계약 목록 자체는 정상 반환된다.
     */
    fun getMyContracts(principal: MemberPrincipal, page: Int, size: Int): PageResult<ContractResponse> {
        val raw = contractService.getMyContracts(principal.id, page, size)
        val stopIds = raw.items.map { it.stopId }.toSet()
        val stopMap = stopResolutionService.resolveStops(stopIds)
        val enriched = raw.items.map { row ->
            val info = stopMap[row.stopId]
            row.copy(stopNumber = info?.stopId, stopName = info?.stopName)
        }
        return PageResult(
            items = enriched,
            totalCnt = raw.totalCnt,
            pageRows = raw.pageRows,
            pageNum = raw.pageNum,
        )
    }

    /** 본인 계약 단건 상세 + 정류소 정보 결합. 단건이어도 fallback을 위해 resolveStops 재사용. */
    fun getContractDetail(principal: MemberPrincipal, contractId: UUID): ContractResponse {
        val raw = contractService.getContractDetail(principal.id, contractId)
        val info = stopResolutionService.resolveStops(setOf(raw.stopId))[raw.stopId]
        return raw.copy(stopNumber = info?.stopId, stopName = info?.stopName)
    }
}
