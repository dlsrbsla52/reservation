package com.media.bus.reservation.reservation.service

import com.media.bus.contract.security.MemberPrincipal
import com.media.bus.reservation.reservation.dto.request.CreateStopReservationRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * ## 예약 생성 흐름을 조율하는 Facade
 *
 * MSA 환경에서 SQS/이벤트 기반 분산 트랜잭션 적용이 어려운 현재 단계에서,
 * 각 서비스 호출을 순서대로 조합하고 트랜잭션을 명시적으로 분리한다.
 */
@Service
class ReservationFacade(
    private val stopResolutionService: StopResolutionService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun createReservation(principal: MemberPrincipal, request: CreateStopReservationRequest) {
        // 1단계: stop 서비스에서 정류소 존재 여부 확인 (S2S, 트랜잭션 외부)
        val stop = stopResolutionService.resolveStop(request.stopId)
        log.debug("[ReservationFacade] 정류소 확인 완료: stopName={}, memberId={}", stop.stopName, principal.id)

        // 2단계: 예약 생성 로직 (추후 구현)
    }
}
