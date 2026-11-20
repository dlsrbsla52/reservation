package com.media.bus.stop.service

import com.media.bus.common.exceptions.BusinessException
import com.media.bus.common.result.type.CommonResult
import com.media.bus.common.validation.InputValidator
import com.media.bus.common.web.wrapper.PageResult
import com.media.bus.stop.dto.request.SimpleStopCreateRequest
import com.media.bus.stop.dto.request.StopSearchCriteria
import com.media.bus.stop.dto.response.BusStopResponse
import com.media.bus.stop.entity.StopEntity
import com.media.bus.stop.entity.enums.StopType
import com.media.bus.stop.guard.StopCommandGuard
import com.media.bus.stop.repository.StopPriceRepository
import com.media.bus.stop.repository.StopRepository
import com.media.bus.stop.result.StopResult
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class StopService(
    private val stopRepository: StopRepository,
    private val stopPriceRepository: StopPriceRepository,
    private val stopCommandGuard: StopCommandGuard,
) {

    /** 정류소 단건 수기 등록 (등록자 id 직접 지정). */
    @Transactional
    fun createOneStop(registeredById: UUID, request: SimpleStopCreateRequest) {
        InputValidator.validate(request)
        stopCommandGuard.validateNotDuplicate(request.stopId)
        StopEntity.createFromRequest(request, registeredById)
    }

    /** pk, stopId, stopName 중 하나의 기준으로 정류소 조회. */
    @Transactional(readOnly = true)
    fun getBusStop(criteria: StopSearchCriteria): List<BusStopResponse> {
        val entities = when (criteria) {
            is StopSearchCriteria.ByPk -> stopRepository.findByIdIn(criteria.pk)
            is StopSearchCriteria.ByStopId -> listOfNotNull(stopRepository.findByStopId(criteria.stopId))
            is StopSearchCriteria.ByStopName -> stopRepository.findByStopNameStartingWith(criteria.stopName)
        }
        if (criteria is StopSearchCriteria.ByStopName && entities.size > StopRepository.MAX_NAME_RESULTS) {
            throw BusinessException(StopResult.STOP_NAME_RESULT_LIMIT_EXCEEDED)
        }
        return withUnitPrice(entities)
    }

    /** 전체 정류소를 페이지네이션으로 조회한다 (관리자 목록 화면용). */
    @Transactional(readOnly = true)
    fun getBusStopPage(
        page: Int,
        size: Int,
        keyword: String?,
        stopsType: String? = null,
    ): PageResult<BusStopResponse> {
        if (page < 0 || size !in 1..100 || (keyword?.length ?: 0) > 200) {
            throw BusinessException(StopResult.INVALID_PAGE_REQUEST)
        }
        val kw = keyword?.trim()?.takeIf { it.isNotEmpty() }
        val type = StopType.parseFilter(stopsType)
        return PageResult(
            items = withUnitPrice(stopRepository.findListPaged(kw, type, page, size)),
            totalCnt = stopRepository.countList(kw, type),
            pageRows = size,
            pageNum = page,
        )
    }

    /** pk(UUID) 복수 기반 일괄 조회. */
    @Transactional(readOnly = true)
    fun getBusStopsByIds(ids: Collection<UUID>): List<BusStopResponse> {
        if (ids.isEmpty() || ids.size > MAX_BULK_LOOKUP_SIZE) throw BusinessException(CommonResult.VALIDATION_FAIL)
        return withUnitPrice(stopRepository.findByIdIn(ids))
    }

    /** 정류소 목록에 현재 단가를 붙인다. */
    private fun withUnitPrice(entities: List<StopEntity>): List<BusStopResponse> {
        if (entities.isEmpty()) return emptyList()
        val priceMap = stopPriceRepository.findUnitPriceMapByStopIds(entities.map { it.id.value })
        return entities.map { BusStopResponse.of(it, priceMap[it.id.value]) }
    }

    companion object {
        const val MAX_BULK_LOOKUP_SIZE = 200
    }
}
