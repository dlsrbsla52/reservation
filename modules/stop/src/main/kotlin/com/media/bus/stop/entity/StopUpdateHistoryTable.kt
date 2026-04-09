package com.media.bus.stop.entity

import com.media.bus.common.entity.common.DateBaseTable
import com.media.bus.stop.entity.enums.ChangeSource
import com.media.bus.stop.entity.enums.StopType

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
    val oldXCrd       = varchar("old_x_crd", 50).nullable()
    val newXCrd       = varchar("new_x_crd", 50).nullable()
    val oldYCrd       = varchar("old_y_crd", 50).nullable()
    val newYCrd       = varchar("new_y_crd", 50).nullable()
    val oldNodeId     = varchar("old_node_id", 50).nullable()
    val newNodeId     = varchar("new_node_id", 50).nullable()
    val oldStopsType  = enumerationByName<StopType>("old_stops_type", 50).nullable()
    val newStopsType  = enumerationByName<StopType>("new_stops_type", 50).nullable()
    val changeSource  = enumerationByName<ChangeSource>("change_source", 50)
}
