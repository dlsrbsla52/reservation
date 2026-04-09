package com.media.bus.stop.entity

import com.media.bus.common.entity.common.DateBaseTable
import com.media.bus.stop.entity.enums.ChangeSource
import com.media.bus.stop.entity.enums.StopType
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
    val xCrd               = varchar("x_crd", 50)
    val yCrd               = varchar("y_crd", 50)
    val nodeId             = varchar("node_id", 50)
    val stopsType          = enumerationByName<StopType>("stops_type", 30)
    val version            = long("version").default(0L)  // 낙관적 잠금 수동 관리
    val registeredById     = javaUUID("registered_by_id").nullable()
    val registeredBySource = enumerationByName<ChangeSource>("registered_by_source", 20)
}
