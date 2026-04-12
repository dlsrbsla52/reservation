package com.media.bus.stop.service

import com.media.bus.contract.security.MemberPrincipal
import com.media.bus.stop.dto.request.SimpleStopCreateRequest
import com.media.bus.stop.dto.request.StopSearchCriteria
import com.media.bus.stop.dto.response.BusStopResponse
import com.media.bus.stop.entity.StopEntity
import com.media.bus.stop.guard.StopCommandGuard
import com.media.bus.stop.repository.StopRepository
import jakarta.validation.Valid
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class StopService(
    private val stopRepository: StopRepository,
    private val stopCommandGuard: StopCommandGuard,
) {

    /**
     * 정류소 단건 수기 등록.
     * Guard는 stopId 중복 등 비즈니스 규칙만 검증합니다.
     *
     * @param principal 인터셉터가 주입한 인증된 회원 정보
     * @param request   등록 요청 DTO
     */
    @Transactional
    fun createOneStop(principal: MemberPrincipal, @Valid request: SimpleStopCreateRequest) {
        stopCommandGuard.validateNotDuplicate(request.stopId)
        StopEntity.createFromRequest(request, principal.id)
    }

    /**
     * pk, stopId, stopName 중 하나의 기준으로 정류소 조회.
     * - pk / stopId : unique 컬럼이므로 0~1건 반환
     * - stopName    : non-unique이므로 동명 정류소 다건 반환 가능
     */
    @Transactional(readOnly = true)
    fun getBusStop(criteria: StopSearchCriteria): List<BusStopResponse> = when (criteria) {
        is StopSearchCriteria.ByPk       -> listOfNotNull(stopRepository.findById(criteria.pk)).map(BusStopResponse::of)
        is StopSearchCriteria.ByStopId   -> listOfNotNull(stopRepository.findByStopId(criteria.stopId)).map(BusStopResponse::of)
        is StopSearchCriteria.ByStopName -> stopRepository.findByStopNameStartingWith(criteria.stopName).map(BusStopResponse::of)
    }

    /**
     * pk(UUID) 복수 기반 일괄 조회.
     *
     * 예약 서비스가 본인 예약 목록 조회 시 각 예약의 정류소 정보를 **단일 S2S 호출로** 붙이기 위해 사용한다.
     * 반환 순서는 입력 순서와 무관하며, 일부 id가 누락되어도 정상 처리한다(예약 목록에서 stopName null로 fallback).
     */
    @Transactional(readOnly = true)
    fun getBusStopsByIds(ids: Collection<UUID>): List<BusStopResponse> =
        stopRepository.findByIdIn(ids).map(BusStopResponse::of)
}
