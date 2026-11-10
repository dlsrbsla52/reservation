package com.media.bus.reservation.reservation.entity

import com.media.bus.common.entity.common.DateBaseTable
import com.media.bus.reservation.reservation.entity.enums.ReservationStatus
import com.media.bus.reservation.slot.StopSlot
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

/**
 * ## 예약 테이블 정의
 *
 * `reservation.reservation` 테이블에 매핑된다.
 *
 * 선점 제약은 DB 에 있다: 부분 유니크 인덱스 `uq_reservation_active_slot`
 * (`(stop_id, slot_no) WHERE status IN ('PENDING','CONSULTING')`)이 광고면당 진행 중 예약을 1건으로 강제한다.
 * 부분 인덱스는 Exposed 로 표현하지 않고 Liquibase changelog(V003)에서만 관리한다.
 */
object ReservationTable : DateBaseTable("reservation.reservation") {
    val memberId = javaUUID("member_id")
    val stopId = javaUUID("stop_id")
    val slotNo = short("slot_no").default(StopSlot.DEFAULT_NO)
    val status = enumerationByName<ReservationStatus>("status", 18)
    val holdExpiresAt = timestampWithTimeZone("hold_expires_at").nullable()
    val consultationRequestedAt = timestampWithTimeZone("consultation_requested_at")
    val desiredContractStartDate = date("desired_contract_start_date").nullable()
    val assigneeId = javaUUID("assignee_id").nullable()
}
