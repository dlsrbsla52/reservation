package com.media.bus.reservation.reservation.entity

import com.media.bus.common.entity.common.DateBaseTable

/**
 * ## 예약 상담 테이블 정의
 *
 * `reservation.reservation_consultation` 테이블에 매핑된다.
 * `reservation_id` FK로 `ReservationTable`을 참조한다.
 */
object ReservationConsultationTable : DateBaseTable("reservation.reservation_consultation") {
    val reservationId = reference("reservation_id", ReservationTable)
    val status = varchar("status", 18)
    val note = text("note").nullable()
}
