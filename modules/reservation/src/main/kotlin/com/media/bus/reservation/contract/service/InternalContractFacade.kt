package com.media.bus.reservation.contract.service

import com.media.bus.common.web.wrapper.PageResult
import com.media.bus.reservation.contract.dto.response.AdminContractView
import com.media.bus.reservation.reservation.service.StopResolutionService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.*

/**
 * ## 관리자 전용 계약 조회 Facade
 *
 * 설계 의도:
 * - `ContractFacade`와 동일한 패턴으로 트랜잭션 경계와 S2S 호출 경계를 분리한다.
 *   - 1단계(트랜잭션 내부): `InternalContractService` → DB에서 Contract + ContractDetail 조회
 *   - 2단계(트랜잭션 외부): `StopResolutionService` → stop 서비스 bulk 조회 후 정류소 정보 주입
 * - 본인 인증 흐름과 달리 매니저 ID를 외부에서 주입받는다. 호출 측인 iam 어드민 모듈이
 *   이미 ADMIN 권한(`@Authorize`) 검증을 마쳤음을 전제로 동작한다.
 *
 * Fallback 정책:
 * - stop 서비스 장애 시 `StopResolutionService.resolveStops`가 빈 맵으로 fallback 하므로
 *   계약 목록 자체는 정상 반환되고, 누락된 stopId 행은 stopName/stopNumber가 null로 노출된다.
 */
@Service
class InternalContractFacade(
    private val internalContractService: InternalContractService,
    private val stopResolutionService: StopResolutionService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 매니저(memberId) 기준 계약 목록을 정류소 정보와 결합하여 반환한다.
     *
     * @param memberId 조회 대상 매니저 회원 ID
     * @param page     0-base 페이지 번호
     * @param size     페이지 크기
     */
    fun getContractsByMemberId(memberId: UUID, page: Int, size: Int): PageResult<AdminContractView> {
        val raw = internalContractService.getContractsByMemberId(memberId, page, size)

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
}
