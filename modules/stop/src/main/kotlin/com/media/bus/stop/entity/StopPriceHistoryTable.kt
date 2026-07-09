package com.media.bus.stop.entity

import com.media.bus.common.entity.common.BaseTable
import com.media.bus.stop.entity.enums.StopPriceChangeType
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * ## 정류소 단가 변경 이력 테이블
 *
 * 스키마: `stop`, 테이블: `stop_price_history`.
 * 등록/수정/삭제를 하나의 이력으로 통합 기록한다 (append-only).
 * `previousPrice`는 최초 등록 시 NULL, `newPrice`는 삭제 시 NULL이다.
 * 모든 UUID 컬럼은 해당 대상을 논리적으로 참조한다 (DB FK 미설정, 애플리케이션 레벨 관리).
 */
object StopPriceHistoryTable : BaseTable("stop.stop_price_history") {
    val stopId = javaUUID("stop_id").index("idx_stop_price_history_stop_id")
    val previousPrice = decimal("previous_price", 15, 2).nullable()
    val newPrice = decimal("new_price", 15, 2).nullable()
    val changeType = enumerationByName<StopPriceChangeType>("change_type", 20)
    val changedById = javaUUID("changed_by_id").nullable()
    val createdAt = timestampWithTimeZone("created_at")
        .also { it.defaultValueFun = { OffsetDateTime.now(ZoneId.of("Asia/Seoul")) } }
}
