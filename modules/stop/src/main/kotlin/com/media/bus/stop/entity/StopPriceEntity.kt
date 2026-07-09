package com.media.bus.stop.entity

import com.media.bus.common.entity.common.DateBaseEntity
import com.media.bus.common.entity.common.UuidV7
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.java.UUIDEntityClass
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.*

class StopPriceEntity(id: EntityID<UUID>) : DateBaseEntity(id, StopPriceTable) {

    companion object : UUIDEntityClass<StopPriceEntity>(StopPriceTable) {

        /** 정류소 단가 신규 등록. Guard가 정류소 존재 여부와 중복 등록 여부를 사전 검증한다. */
        fun create(stopId: UUID, unitPrice: BigDecimal, registeredById: UUID?): StopPriceEntity =
            new(UuidV7.generate()) {
                this.stopId = stopId
                this.unitPrice = unitPrice
                this.registeredById = registeredById
            }
    }

    var stopId         by StopPriceTable.stopId
    var unitPrice      by StopPriceTable.unitPrice
    var registeredById by StopPriceTable.registeredById

    /** 단가를 변경하고 수정일시를 갱신한다. */
    fun updatePrice(newPrice: BigDecimal) {
        unitPrice = newPrice
        updatedAt = OffsetDateTime.now(ZoneId.of("Asia/Seoul"))
    }
}
