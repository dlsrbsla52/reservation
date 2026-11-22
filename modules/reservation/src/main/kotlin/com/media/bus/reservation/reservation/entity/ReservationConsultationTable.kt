package com.media.bus.reservation.reservation.entity

import com.media.bus.common.entity.common.DateBaseTable
import com.media.bus.reservation.reservation.entity.enums.ReservationStatus

/** 예약 상담 테이블 정의 */
object ReservationConsultationTable : DateBaseTable("reservation.reservation_consultation") {
    val reservationId = reference("reservation_id", ReservationTable)
    val status = enumerationByName<ReservationStatus>("status", 18)
    val note = text("note").nullable()
}
