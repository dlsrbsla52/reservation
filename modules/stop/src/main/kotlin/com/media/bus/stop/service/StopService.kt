package com.media.bus.stop.service

import com.media.bus.common.web.wrapper.PageResult
import com.media.bus.contract.security.MemberPrincipal
import com.media.bus.stop.dto.request.SimpleStopCreateRequest
import com.media.bus.stop.dto.request.StopSearchCriteria
import com.media.bus.stop.dto.response.BusStopResponse
import com.media.bus.stop.entity.StopEntity
import com.media.bus.stop.entity.enums.StopType
import com.media.bus.stop.guard.StopCommandGuard
import com.media.bus.stop.repository.StopPriceRepository
import com.media.bus.stop.repository.StopRepository
import jakarta.validation.Valid
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class StopService(
    private val stopRepository: StopRepository,
    private val stopPriceRepository: StopPriceRepository,
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
    fun getBusStop(criteria: StopSearchCriteria): List<BusStopResponse> {
        val entities = when (criteria) {
            is StopSearchCriteria.ByPk       -> stopRepository.findByIdIn(criteria.pk)
            is StopSearchCriteria.ByStopId   -> listOfNotNull(stopRepository.findByStopId(criteria.stopId))
            is StopSearchCriteria.ByStopName -> stopRepository.findByStopNameStartingWith(criteria.stopName)
        }
        return withUnitPrice(entities)
    }

    /**
     * 전체 정류소를 페이지네이션으로 조회한다 (관리자 목록 화면용).
     * keyword가 주어지면 정류소명/정류소번호 부분 일치로, stopsType이 주어지면 해당 유형으로 필터링한다.
     * 두 조건은 AND로 결합된다.
     *
     * 단가는 현재 페이지의 정류소 pk 집합으로 **1회 배치 조회**하여 붙인다(정류소당 1회 호출 금지).
     *
     * @param stopsType 유형 필터 문자열. 알 수 없는 값이면 [StopType.parseFilter]가 400을 던진다.
     */
    @Transactional(readOnly = true)
    fun getBusStopPage(
        page: Int,
        size: Int,
        keyword: String?,
        stopsType: String? = null,
    ): PageResult<BusStopResponse> {
        val kw = keyword?.trim()?.takeIf { it.isNotEmpty() }
        val type = StopType.parseFilter(stopsType)
        return PageResult(
            items = withUnitPrice(stopRepository.findListPaged(kw, type, page, size)),
            totalCnt = stopRepository.countList(kw, type),
            pageRows = size,
            pageNum = page,
        )
    }

    /**
     * pk(UUID) 복수 기반 일괄 조회.
     *
     * 예약 서비스가 본인 예약 목록 조회 시 각 예약의 정류소 정보를 **단일 S2S 호출로** 붙이기 위해 사용한다.
     * 반환 순서는 입력 순서와 무관하며, 일부 id가 누락되어도 정상 처리한다(예약 목록에서 stopName null로 fallback).
     */
    @Transactional(readOnly = true)
    fun getBusStopsByIds(ids: Collection<UUID>): List<BusStopResponse> =
        withUnitPrice(stopRepository.findByIdIn(ids))

    /**
     * 정류소 목록에 현재 단가를 붙인다.
     *
     * 설계 의도:
     * - 모든 조회 경로가 동일한 방식으로 단가를 채워, 응답의 `unitPrice == null`이
     *   항상 "**단가 미등록**"만 의미하도록 보장한다(경로별로 "조회 안 함"이 섞이지 않게).
     * - 단가 조회는 정류소 건수와 무관하게 **쿼리 1회**다.
     *
     * 사이드 이펙트 주의: 정류소 목록이 비어 있으면 DB 조회 자체를 생략한다.
     */
    private fun withUnitPrice(entities: List<StopEntity>): List<BusStopResponse> {
        if (entities.isEmpty()) return emptyList()
        val priceMap = stopPriceRepository.findUnitPriceMapByStopIds(entities.map { it.id.value })
        return entities.map { BusStopResponse.of(it, priceMap[it.id.value]) }
    }
}
