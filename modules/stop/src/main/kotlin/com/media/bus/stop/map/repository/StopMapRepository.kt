package com.media.bus.stop.map.repository

import com.media.bus.stop.entity.StopTable
import com.media.bus.stop.map.dto.StopBoundsCriteria
import com.media.bus.stop.map.dto.StopMarkerResponse
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.select
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

/** 지도 전용 정류소 조회 저장소 (읽기 전용) */
@Repository
class StopMapRepository {

    /** bounding box 안의 정류소를 조회한다. */
    @Transactional(readOnly = true)
    fun findMarkersInBounds(criteria: StopBoundsCriteria): List<StopMarkerResponse> =
        StopTable
            .select(
                StopTable.id,
                StopTable.stopId,
                StopTable.stopName,
                StopTable.xCrd,
                StopTable.yCrd,
                StopTable.stopsType,
            )
            .where {
                val bbox = (StopTable.yCrd greaterEq criteria.swLat) and
                    (StopTable.yCrd lessEq criteria.neLat) and
                    (StopTable.xCrd greaterEq criteria.swLng) and
                    (StopTable.xCrd lessEq criteria.neLng)
                criteria.stopsType?.let { bbox and (StopTable.stopsType eq it) } ?: bbox
            }
            .orderBy(StopTable.yCrd to SortOrder.ASC, StopTable.xCrd to SortOrder.ASC)
            .limit(criteria.limit + 1)
            .map {
                StopMarkerResponse(
                    id = it[StopTable.id].value,
                    stopId = it[StopTable.stopId],
                    stopName = it[StopTable.stopName],
                    lat = it[StopTable.yCrd],
                    lng = it[StopTable.xCrd],
                    stopsType = it[StopTable.stopsType],
                )
            }
}
