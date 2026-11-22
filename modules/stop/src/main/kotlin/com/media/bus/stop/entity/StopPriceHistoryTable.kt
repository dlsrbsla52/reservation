package com.media.bus.stop.entity

import com.media.bus.common.entity.common.BaseTable
import com.media.bus.stop.entity.enums.StopPriceChangeType
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import java.time.OffsetDateTime
import java.time.ZoneId

/** 정류소 단가 변경 이력 테이블 */
object StopPriceHistoryTable : BaseTable("stop.stop_price_history") {
    val stopId = javaUUID("stop_id").index("idx_stop_price_history_stop_id")
    val previousPrice = decimal("previous_price", 15, 2).nullable()
    val newPrice = decimal("new_price", 15, 2).nullable()
    val changeType = enumerationByName<StopPriceChangeType>("change_type", 20)
    val changedById = javaUUID("changed_by_id").nullable()
    val createdAt = timestampWithTimeZone("created_at")
        .also { it.defaultValueFun = { OffsetDateTime.now(ZoneId.of("Asia/Seoul")) } }
}
