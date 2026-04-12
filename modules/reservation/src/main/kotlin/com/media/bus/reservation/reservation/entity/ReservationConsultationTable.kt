package com.media.bus.reservation.reservation.entity

import com.media.bus.common.entity.common.DateBaseTable
import com.media.bus.reservation.reservation.entity.enums.ReservationStatus

/**
 * ## 예약 상담 테이블 정의
 *
 * `reservation.reservation_consultation` 테이블에 매핑된다.
 * `reservation_id` FK 로 `ReservationTable` 을 참조한다.
 *
 * 설계 의도:
 * - `status` 는 `ReservationStatus` 로 enum 매핑한다. 기존 스키마(VARCHAR(18))와 호환되며
 *   Exposed 가 enum name 을 문자열로 저장/조회한다.
 * - 예약의 현재 상태는 이 테이블에서 `reservation_id` 기준 최신 row 의 `status` 로 결정한다.
 */
object ReservationConsultationTable : DateBaseTable("reservation.reservation_consultation") {
    val reservationId = reference("reservation_id", ReservationTable)
    val status = enumerationByName<ReservationStatus>("status", 18)
    val note = text("note").nullable()
}
