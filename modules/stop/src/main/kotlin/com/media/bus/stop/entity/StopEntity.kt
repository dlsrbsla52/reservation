package com.media.bus.stop.entity

import com.media.bus.common.entity.common.DateBaseEntity
import com.media.bus.common.entity.common.UuidV7
import com.media.bus.stop.dto.external.SeoulBusStopRow
import com.media.bus.stop.dto.request.SimpleStopCreateRequest
import com.media.bus.stop.entity.enums.ChangeSource
import com.media.bus.stop.entity.enums.StopType
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.java.UUIDEntityClass
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.*

/**
 * ## 정류소 Exposed DAO 엔티티
 *
 * JPA `Stop` 엔티티를 대체한다.
 * 팩토리 메서드(`createFromRequest`, `createFromPublicApi`)로만 생성을 허용하여
 * 불완전한 상태의 인스턴스 생성을 방지한다.
 */
class StopEntity(id: EntityID<UUID>) : DateBaseEntity(id, StopTable) {

    companion object : UUIDEntityClass<StopEntity>(StopTable) {

        /** 수동 등록 팩토리 -- 관리자가 단건 등록할 때 사용 */
        fun createFromRequest(request: SimpleStopCreateRequest, registeredById: UUID): StopEntity =
            new(UuidV7.generate()) {
                stopId = request.stopId
                stopName = request.stopName
                xCrd = request.xCrd
                yCrd = request.yCrd
                nodeId = request.nodeId
                stopsType = request.stopsType
                version = 0L
                this.registeredById = registeredById
                registeredBySource = ChangeSource.USER
            }

        /** 공공 API 팩토리 -- 서울 열린데이터광장 데이터로 신규 등록할 때 사용 */
        fun createFromPublicApi(row: SeoulBusStopRow): StopEntity =
            new(UuidV7.generate()) {
                stopId = row.stopsNo
                stopName = row.stopsName
                xCrd = row.xCrd
                yCrd = row.yCrd
                nodeId = row.nodeId
                stopsType = StopType.fromDisplayName(row.stopsType)
                version = 0L
                registeredById = null
                registeredBySource = ChangeSource.SYSTEM
            }
    }

    var stopId             by StopTable.stopId
    var stopName           by StopTable.stopName
    var xCrd               by StopTable.xCrd
    var yCrd               by StopTable.yCrd
    var nodeId             by StopTable.nodeId
    var stopsType          by StopTable.stopsType
    var version            by StopTable.version
    var registeredById     by StopTable.registeredById
    var registeredBySource by StopTable.registeredBySource

    /**
     * 공공 API 데이터와 현재 엔티티를 비교해 변경이 있으면 필드를 갱신하고 히스토리 엔티티를 반환한다.
     * 변경이 없으면 null을 반환한다.
     */
    fun applyUpdate(row: SeoulBusStopRow, changeSource: ChangeSource): StopUpdateHistoryEntity? {
        val newType = StopType.fromDisplayName(row.stopsType)
        val changed = stopName != row.stopsName
            || xCrd != row.xCrd
            || yCrd != row.yCrd
            || nodeId != row.nodeId
            || stopsType != newType

        if (!changed) return null

        // 변경 이력 기록 후 현재 값 업데이트
        val history = StopUpdateHistoryEntity.create(
            stop = this,
            newRow = row,
            newType = newType,
            changeSource = changeSource,
        )
        stopName = row.stopsName
        xCrd = row.xCrd
        yCrd = row.yCrd
        nodeId = row.nodeId
        stopsType = newType
        updatedAt = OffsetDateTime.now(ZoneId.of("Asia/Seoul"))
        return history
    }
}
