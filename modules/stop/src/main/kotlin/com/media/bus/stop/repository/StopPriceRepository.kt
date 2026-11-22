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

    /** 정류소 pk 목록에 해당하는 단가를 **단일 쿼리**로 일괄 조회한다. */
    @Transactional(readOnly = true)
    fun findUnitPriceMapByStopIds(stopIds: Collection<UUID>): Map<UUID, BigDecimal> {
        if (stopIds.isEmpty()) return emptyMap()
        return StopPriceTable
            .select(StopPriceTable.stopId, StopPriceTable.unitPrice)
            .where { StopPriceTable.stopId inList stopIds.toList() }
            .associate { it[StopPriceTable.stopId] to it[StopPriceTable.unitPrice] }
    }
}
