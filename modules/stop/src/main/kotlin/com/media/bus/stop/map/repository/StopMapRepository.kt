package com.media.bus.stop.map.repository

import com.media.bus.stop.entity.StopTable
import com.media.bus.stop.map.dto.StopBoundsCriteria
import com.media.bus.stop.map.dto.StopMarkerResponse
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.select
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

/**
 * ## 지도 전용 정류소 조회 저장소 (읽기 전용)
 *
 * `StopRepository` 와 분리한 이유:
 * - CRUD 경로는 `StopEntity` DAO 를 다루지만, 지도 조회는 **엔티티가 전혀 필요 없다.**
 *   DAO 로 wrap 하면 사용하지도 않는 컬럼까지 읽고 영속성 컨텍스트에 수천 건을 적재하게 된다.
 * - 여기서는 마커에 필요한 4개 컬럼만 projection 해 곧바로 응답 DTO 로 매핑한다.
 */
@Repository
class StopMapRepository {

    /**
     * bounding box 안의 정류소를 조회한다.
     *
     * `idx_stop_coord (y_crd, x_crd)` 인덱스를 태우기 위해 **위도 조건을 먼저** 둔다.
     * 선행 컬럼인 위도로 범위를 좁힌 뒤 경도로 필터링하는 것이 인덱스 사용 계획에 유리하다.
     *
     * 결과 잘림 여부를 판정하기 위해 `limit + 1` 건을 읽는다.
     * COUNT 쿼리를 따로 날리면 같은 범위를 두 번 스캔하게 되므로, 한 건 더 읽어 판정만 한다.
     *
     * @return 최대 `limit + 1` 건. 잘림 판정은 호출자가 수행한다.
     */
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
                // 유형 조건은 bbox 뒤에 붙인다. 범위 스캔은 idx_stop_coord 가 담당하고
                // 유형은 그 결과(최대 limit+1 건)에 대한 필터라 별도 인덱스가 필요 없다.
                val bbox = (StopTable.yCrd greaterEq criteria.swLat) and
                    (StopTable.yCrd lessEq criteria.neLat) and
                    (StopTable.xCrd greaterEq criteria.swLng) and
                    (StopTable.xCrd lessEq criteria.neLng)
                criteria.stopsType?.let { bbox and (StopTable.stopsType eq it) } ?: bbox
            }
            // 상한에 걸려 잘리더라도 매 요청 동일한 부분집합이 나오도록 정렬을 고정한다.
            // 정렬이 없으면 팬/줌 때마다 다른 마커가 나타났다 사라져 깜빡임처럼 보인다.
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
