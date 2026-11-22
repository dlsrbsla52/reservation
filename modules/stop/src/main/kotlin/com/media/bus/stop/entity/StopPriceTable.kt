package com.media.bus.stop.entity

import com.media.bus.common.entity.common.DateBaseTable
import org.jetbrains.exposed.v1.core.java.javaUUID

/** 정류소 단가 테이블 정의 */
object StopPriceTable : DateBaseTable("stop.stop_price") {
    val stopId = javaUUID("stop_id").uniqueIndex("uk_stop_price_stop_id")
    val unitPrice = decimal("unit_price", 15, 2)
    val registeredById = javaUUID("registered_by_id").nullable()
}
