package com.media.bus.reservation.reservation.entity

import com.media.bus.common.entity.common.DateBaseTable
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

/**
 * ## 예약 테이블 정의
 *
 * `reservation.reservation` 테이블에 매핑된다.
 */
object ReservationTable : DateBaseTable("reservation.reservation") {
    val memberId = javaUUID("member_id")
    val stopId = javaUUID("stop_id")
    val consultationRequestedAt = timestampWithTimeZone("consultation_requested_at")
    val desiredContractStartDate = date("desired_contract_start_date").nullable()
}
