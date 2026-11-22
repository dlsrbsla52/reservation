package com.media.bus.stop.map.service

import com.media.bus.stop.map.dto.StopBoundsCriteria
import com.media.bus.stop.map.dto.StopBoundsResponse
import com.media.bus.stop.map.repository.StopMapRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 지도 조회 전용 서비스 (읽기 전용 슬라이스) */
@Service
class StopMapQueryService(
    private val stopMapRepository: StopMapRepository,
) {

    /** 지도 영역에 포함된 정류소 마커를 조회한다. */
    @Transactional(readOnly = true)
    fun findMarkersInBounds(criteria: StopBoundsCriteria): StopBoundsResponse {
        val rows = stopMapRepository.findMarkersInBounds(criteria)
        val truncated = rows.size > criteria.limit

        return StopBoundsResponse(
            items = if (truncated) rows.take(criteria.limit) else rows,
            truncated = truncated,
            limit = criteria.limit,
        )
    }
}
