package com.media.bus.stop.entity

import com.media.bus.common.entity.common.DateBaseEntity
import com.media.bus.common.entity.common.UuidV7
import com.media.bus.stop.dto.external.SeoulBusStopRow
import com.media.bus.stop.dto.request.SimpleStopCreateRequest
import com.media.bus.stop.entity.enums.ChangeSource
import com.media.bus.stop.entity.enums.StopType
import com.media.bus.stop.support.Coordinate
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

        /**
         * 수동 등록 팩토리 -- 관리자가 단건 등록할 때 사용.
         *
         * 좌표가 숫자 형식이 아니거나 위경도 범위를 벗어나면 [Coordinate] 가
         * `400 BAD_REQUEST` 예외를 던져 등록을 거부한다.
         */
        fun createFromRequest(request: SimpleStopCreateRequest, registeredById: UUID): StopEntity =
            new(UuidV7.generate()) {
                stopId = request.stopId
                stopName = request.stopName
                xCrd = Coordinate.parseLongitude(request.xCrd)
                yCrd = Coordinate.parseLatitude(request.yCrd)
                nodeId = request.nodeId
                stopsType = request.stopsType
                version = 0L
                this.registeredById = registeredById
                registeredBySource = ChangeSource.USER
            }

        /**
         * 공공 API 팩토리 -- 서울 열린데이터광장 데이터로 신규 등록할 때 사용.
         *
         * **선행 조건:** 호출 전에 [Coordinate.parseOrNullLongitude] / [Coordinate.parseOrNullLatitude] 로
         * 좌표 유효성을 확인해야 한다. 일괄 적재 경로에서는 `StopBulkPersistService` 가 이를 담당하며,
         * 불량 행은 예외 대신 건너뛴다(한 건 때문에 배치 전체가 중단되지 않도록).
         */
        fun createFromPublicApi(row: SeoulBusStopRow): StopEntity =
            new(UuidV7.generate()) {
                stopId = row.stopsNo
                stopName = row.stopsName
                xCrd = Coordinate.parseLongitude(row.xCrd)
                yCrd = Coordinate.parseLatitude(row.yCrd)
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
        val newXCrd = Coordinate.parseLongitude(row.xCrd)
        val newYCrd = Coordinate.parseLatitude(row.yCrd)

        // 좌표는 BigDecimal.equals 가 scale 까지 비교하므로 반드시 Coordinate.isSame(compareTo)을 사용한다.
        // equals 로 비교하면 값이 같아도 scale 차이로 매 동기화마다 변경으로 오탐지된다.
        val changed = stopName != row.stopsName
            || !Coordinate.isSame(xCrd, newXCrd)
            || !Coordinate.isSame(yCrd, newYCrd)
            || nodeId != row.nodeId
            || stopsType != newType

        if (!changed) return null

        // 변경 이력 기록 후 현재 값 업데이트
        val history = StopUpdateHistoryEntity.create(
            stop = this,
            newRow = row,
            newXCrd = newXCrd,
            newYCrd = newYCrd,
            newType = newType,
            changeSource = changeSource,
        )
        stopName = row.stopsName
        xCrd = newXCrd
        yCrd = newYCrd
        nodeId = row.nodeId
        stopsType = newType
        updatedAt = OffsetDateTime.now(ZoneId.of("Asia/Seoul"))
        return history
    }
}
