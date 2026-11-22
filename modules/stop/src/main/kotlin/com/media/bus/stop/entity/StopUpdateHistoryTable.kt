package com.media.bus.stop.entity

import com.media.bus.common.entity.common.DateBaseTable
import com.media.bus.stop.entity.enums.ChangeSource
import com.media.bus.stop.entity.enums.StopType
import com.media.bus.stop.support.Coordinate

/** 정류소 변경 이력 테이블 정의 */
object StopUpdateHistoryTable : DateBaseTable("stop.stop_update_history") {
    val stopId = reference("stop_id", StopTable)
    val oldStopName = varchar("old_stop_name", 50).nullable()
    val newStopName = varchar("new_stop_name", 50).nullable()
    val oldXCrd = decimal("old_x_crd", 10, Coordinate.SCALE).nullable()
    val newXCrd = decimal("new_x_crd", 10, Coordinate.SCALE).nullable()
    val oldYCrd = decimal("old_y_crd", 10, Coordinate.SCALE).nullable()
    val newYCrd = decimal("new_y_crd", 10, Coordinate.SCALE).nullable()
    val oldNodeId = varchar("old_node_id", 50).nullable()
    val newNodeId = varchar("new_node_id", 50).nullable()
    val oldStopsType = enumerationByName<StopType>("old_stops_type", 50).nullable()
    val newStopsType = enumerationByName<StopType>("new_stops_type", 50).nullable()
    val changeSource = enumerationByName<ChangeSource>("change_source", 50)
}
