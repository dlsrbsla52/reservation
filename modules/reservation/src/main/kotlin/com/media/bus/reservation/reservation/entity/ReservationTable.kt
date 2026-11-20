package com.media.bus.reservation.reservation.entity

import com.media.bus.common.entity.common.DateBaseTable
import com.media.bus.reservation.reservation.entity.enums.ReservationStatus
import com.media.bus.reservation.slot.StopSlot
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

/** 예약 테이블 정의 */
object ReservationTable : DateBaseTable("reservation.reservation") {
    val memberId = javaUUID("member_id")
    val stopId = javaUUID("stop_id")
    val slotNo = short("slot_no").default(StopSlot.DEFAULT_NO)
    val status = enumerationByName<ReservationStatus>("status", 18)
    val holdExpiresAt = timestampWithTimeZone("hold_expires_at").nullable()
    val consultationRequestedAt = timestampWithTimeZone("consultation_requested_at")
    val desiredContractStartDate = date("desired_contract_start_date")
    val assigneeId = javaUUID("assignee_id").nullable()
}
