package com.media.bus.stop.entity

import com.media.bus.common.entity.common.DateBaseTable
import com.media.bus.stop.entity.enums.ChangeSource
import com.media.bus.stop.entity.enums.StopType
import com.media.bus.stop.support.Coordinate

/**
 * ## 정류소 변경 이력 테이블 정의
 *
 * 스키마: `stop`, 테이블: `stop_update_history`
 * `stop_id` FK로 `StopTable`을 참조한다.
 */
object StopUpdateHistoryTable : DateBaseTable("stop.stop_update_history") {
    val stopId        = reference("stop_id", StopTable)  // FK -> StopTable
    val oldStopName   = varchar("old_stop_name", 50).nullable()
    val newStopName   = varchar("new_stop_name", 50).nullable()
    // 본 테이블(StopTable)과 좌표 타입을 일치시킨다 — 이력 비교 시 문자열/숫자 혼용을 방지
    val oldXCrd       = decimal("old_x_crd", 10, Coordinate.SCALE).nullable()
    val newXCrd       = decimal("new_x_crd", 10, Coordinate.SCALE).nullable()
    val oldYCrd       = decimal("old_y_crd", 10, Coordinate.SCALE).nullable()
    val newYCrd       = decimal("new_y_crd", 10, Coordinate.SCALE).nullable()
    val oldNodeId     = varchar("old_node_id", 50).nullable()
    val newNodeId     = varchar("new_node_id", 50).nullable()
    val oldStopsType  = enumerationByName<StopType>("old_stops_type", 50).nullable()
    val newStopsType  = enumerationByName<StopType>("new_stops_type", 50).nullable()
    val changeSource  = enumerationByName<ChangeSource>("change_source", 50)
}
