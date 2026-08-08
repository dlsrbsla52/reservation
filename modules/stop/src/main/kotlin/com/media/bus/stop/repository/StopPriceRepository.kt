package com.media.bus.stop.repository

import com.media.bus.stop.entity.StopPriceEntity
import com.media.bus.stop.entity.StopPriceTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.select
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.*

@Repository
class StopPriceRepository {

    @Transactional(readOnly = true)
    fun findByStopId(stopId: UUID): StopPriceEntity? =
        StopPriceEntity.find { StopPriceTable.stopId eq stopId }.firstOrNull()

    /**
     * 정류소 pk 목록에 해당하는 단가를 **단일 쿼리**로 일괄 조회한다.
     *
     * 설계 의도:
     * - 어드민 목록 화면이 정류소마다 단가를 조회하던 N+1을 제거하기 위한 배치 조회.
     * - 엔티티 대신 `stop_id`/`unit_price` 두 컬럼만 projection 하여 DAO 캐시 적재 비용을 줄인다.
     * - `uk_stop_price_stop_id` UNIQUE 인덱스 덕분에 stop_id당 최대 1건이므로 Map 변환 시 키 충돌이 없다.
     *
     * @return stopId(정류소 pk) → unitPrice 매핑. 단가 미등록 정류소는 **키 자체가 존재하지 않는다**.
     */
    @Transactional(readOnly = true)
    fun findUnitPriceMapByStopIds(stopIds: Collection<UUID>): Map<UUID, BigDecimal> {
        if (stopIds.isEmpty()) return emptyMap()
        return StopPriceTable
            .select(StopPriceTable.stopId, StopPriceTable.unitPrice)
            .where { StopPriceTable.stopId inList stopIds.toList() }
            .associate { it[StopPriceTable.stopId] to it[StopPriceTable.unitPrice] }
    }
}
