package com.media.bus.stop.entity

import com.media.bus.common.entity.common.DateBaseTable
import org.jetbrains.exposed.v1.core.java.javaUUID

/**
 * ## 정류소 단가 테이블 정의
 *
 * 스키마: `stop`, 테이블: `stop_price`.
 * `stop_id` UNIQUE 제약으로 정류소당 단가 1건(1:1)을 보장한다.
 * `stop_id`는 `stop.stop.id`를 논리적으로 참조한다 (DB FK 미설정, 애플리케이션 레벨 관리).
 */
object StopPriceTable : DateBaseTable("stop.stop_price") {
    val stopId = javaUUID("stop_id").uniqueIndex("uk_stop_price_stop_id")
    val unitPrice = decimal("unit_price", 15, 2)
    val registeredById = javaUUID("registered_by_id").nullable()
}
