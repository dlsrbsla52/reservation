package com.media.bus.stop.entity

import com.media.bus.common.entity.common.BaseEntity
import com.media.bus.common.entity.common.UuidV7
import com.media.bus.stop.entity.enums.StopPriceChangeType
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.java.UUIDEntityClass
import java.math.BigDecimal
import java.util.*

class StopPriceHistoryEntity(id: EntityID<UUID>) : BaseEntity(id) {

    companion object : UUIDEntityClass<StopPriceHistoryEntity>(StopPriceHistoryTable) {

        fun create(
            stopId: UUID,
            previousPrice: BigDecimal?,
            newPrice: BigDecimal?,
            changeType: StopPriceChangeType,
            changedById: UUID?,
        ): StopPriceHistoryEntity = new(UuidV7.generate()) {
            this.stopId = stopId
            this.previousPrice = previousPrice
            this.newPrice = newPrice
            this.changeType = changeType
            this.changedById = changedById
        }
    }

    var stopId        by StopPriceHistoryTable.stopId
    var previousPrice by StopPriceHistoryTable.previousPrice
    var newPrice      by StopPriceHistoryTable.newPrice
    var changeType    by StopPriceHistoryTable.changeType
    var changedById   by StopPriceHistoryTable.changedById
    val createdAt     by StopPriceHistoryTable.createdAt
}
