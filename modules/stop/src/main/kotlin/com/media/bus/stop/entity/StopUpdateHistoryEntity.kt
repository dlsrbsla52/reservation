package com.media.bus.stop.entity

import com.media.bus.common.entity.common.DateBaseEntity
import com.media.bus.common.entity.common.UuidV7
import com.media.bus.stop.dto.external.SeoulBusStopRow
import com.media.bus.stop.entity.enums.ChangeSource
import com.media.bus.stop.entity.enums.StopType
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.java.UUIDEntityClass
import java.util.*

/**
 * ## 정류소 변경 이력 Exposed DAO 엔티티
 *
 * 정류소 필드가 변경될 때 변경 전후 값을 기록한다.
 * `StopEntity.applyUpdate()`에서만 생성된다.
 */
class StopUpdateHistoryEntity(id: EntityID<UUID>) : DateBaseEntity(id, StopUpdateHistoryTable) {

    companion object : UUIDEntityClass<StopUpdateHistoryEntity>(StopUpdateHistoryTable) {

        /**
         * 정류소 변경 이력 생성 팩토리.
         * 변경 전 값은 기존 `stop`에서, 변경 후 값은 `newRow`에서 추출한다.
         */
        fun create(
            stop: StopEntity,
            newRow: SeoulBusStopRow,
            newType: StopType,
            changeSource: ChangeSource,
        ): StopUpdateHistoryEntity = new(UuidV7.generate()) {
            this.stopId = stop.id
            this.oldStopName = stop.stopName
            this.newStopName = newRow.stopsName
            this.oldXCrd = stop.xCrd
            this.newXCrd = newRow.xCrd
            this.oldYCrd = stop.yCrd
            this.newYCrd = newRow.yCrd
            this.oldNodeId = stop.nodeId
            this.newNodeId = newRow.nodeId
            this.oldStopsType = stop.stopsType
            this.newStopsType = newType
            this.changeSource = changeSource
        }
    }

    var stopId       by StopUpdateHistoryTable.stopId
    var oldStopName  by StopUpdateHistoryTable.oldStopName
    var newStopName  by StopUpdateHistoryTable.newStopName
    var oldXCrd      by StopUpdateHistoryTable.oldXCrd
    var newXCrd      by StopUpdateHistoryTable.newXCrd
    var oldYCrd      by StopUpdateHistoryTable.oldYCrd
    var newYCrd      by StopUpdateHistoryTable.newYCrd
    var oldNodeId    by StopUpdateHistoryTable.oldNodeId
    var newNodeId    by StopUpdateHistoryTable.newNodeId
    var oldStopsType by StopUpdateHistoryTable.oldStopsType
    var newStopsType by StopUpdateHistoryTable.newStopsType
    var changeSource by StopUpdateHistoryTable.changeSource
}
