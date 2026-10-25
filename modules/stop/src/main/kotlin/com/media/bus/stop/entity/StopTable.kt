package com.media.bus.stop.entity

import com.media.bus.common.entity.common.DateBaseTable
import com.media.bus.stop.entity.enums.ChangeSource
import com.media.bus.stop.entity.enums.StopType
import com.media.bus.stop.support.Coordinate
import org.jetbrains.exposed.v1.core.java.javaUUID

/**
 * ## 정류소 테이블 정의
 *
 * 스키마: `stop`, 테이블: `stop`
 * Exposed DAO 매핑용 테이블 object.
 */
object StopTable : DateBaseTable("stop.stop") {
    val stopId             = varchar("stop_id", 50).uniqueIndex()
    val stopName           = varchar("stop_name", 200).index("idx_stop_name")
    // 좌표는 bbox 범위 조회를 위해 NUMERIC(10,7). 인덱스는 (y_crd, x_crd) 순서의 idx_stop_coord
    val xCrd               = decimal("x_crd", 10, Coordinate.SCALE)
    val yCrd               = decimal("y_crd", 10, Coordinate.SCALE)
    val nodeId             = varchar("node_id", 50)
    val stopsType          = enumerationByName<StopType>("stops_type", 30)
    val version            = long("version").default(0L)  // 낙관적 잠금 수동 관리
    val registeredById     = javaUUID("registered_by_id").nullable()
    val registeredBySource = enumerationByName<ChangeSource>("registered_by_source", 20)
}
