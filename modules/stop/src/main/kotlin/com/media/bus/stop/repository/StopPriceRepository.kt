package com.media.bus.stop.repository

import com.media.bus.stop.entity.StopPriceEntity
import com.media.bus.stop.entity.StopPriceTable
import org.jetbrains.exposed.v1.core.eq
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Repository
class StopPriceRepository {

    @Transactional(readOnly = true)
    fun findByStopId(stopId: UUID): StopPriceEntity? =
        StopPriceEntity.find { StopPriceTable.stopId eq stopId }.firstOrNull()
}
