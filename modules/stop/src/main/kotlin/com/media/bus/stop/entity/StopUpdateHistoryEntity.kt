package com.media.bus.stop.entity

import com.media.bus.common.entity.common.DateBaseEntity
import com.media.bus.common.entity.common.UuidV7
import com.media.bus.stop.dto.external.SeoulBusStopRow
import com.media.bus.stop.entity.enums.ChangeSource
import com.media.bus.stop.entity.enums.StopType
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.java.UUIDEntityClass
import java.math.BigDecimal
import java.util.*

/** 정류소 변경 이력 Exposed DAO 엔티티 */
class StopUpdateHistoryEntity(id: EntityID<UUID>) : DateBaseEntity(id, StopUpdateHistoryTable) {

    companion object : UUIDEntityClass<StopUpdateHistoryEntity>(StopUpdateHistoryTable) {

        /** 정류소 변경 이력 생성 팩토리. */
        fun create(
            stop: StopEntity,
            newRow: SeoulBusStopRow,
            newXCrd: BigDecimal,
            newYCrd: BigDecimal,
            newType: StopType,
            changeSource: ChangeSource,
        ): StopUpdateHistoryEntity = new(UuidV7.generate()) {
            this.stopId = stop.id
            this.oldStopName = stop.stopName
            this.newStopName = newRow.stopsName
            this.oldXCrd = stop.xCrd
            this.newXCrd = newXCrd
            this.oldYCrd = stop.yCrd
            this.newYCrd = newYCrd
            this.oldNodeId = stop.nodeId
            this.newNodeId = newRow.nodeId
            this.oldStopsType = stop.stopsType
            this.newStopsType = newType
            this.changeSource = changeSource
        }
    }

    var stopId by StopUpdateHistoryTable.stopId
    var oldStopName by StopUpdateHistoryTable.oldStopName
    var newStopName by StopUpdateHistoryTable.newStopName
    var oldXCrd by StopUpdateHistoryTable.oldXCrd
    var newXCrd by StopUpdateHistoryTable.newXCrd
    var oldYCrd by StopUpdateHistoryTable.oldYCrd
    var newYCrd by StopUpdateHistoryTable.newYCrd
    var oldNodeId by StopUpdateHistoryTable.oldNodeId
    var newNodeId by StopUpdateHistoryTable.newNodeId
    var oldStopsType by StopUpdateHistoryTable.oldStopsType
    var newStopsType by StopUpdateHistoryTable.newStopsType
    var changeSource by StopUpdateHistoryTable.changeSource
}
