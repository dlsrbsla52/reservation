package com.media.bus.reservation.reservation.entity

import com.media.bus.common.entity.common.DateBaseEntity
import com.media.bus.common.entity.common.UuidV7
import com.media.bus.reservation.reservation.entity.enums.ReservationStatus
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.java.UUIDEntityClass
import java.util.*

/**
 * ## 예약 상담 Exposed DAO 엔티티
 *
 * 예약별 상담 이력을 append-only 방식으로 기록한다.
 * 예약 상태 변경은 "새 상담 row 추가"로 표현하며, 최신 row 의 `status` 가 현재 상태이다.
 */
class ReservationConsultationEntity(id: EntityID<UUID>) : DateBaseEntity(id, ReservationConsultationTable) {

    companion object : UUIDEntityClass<ReservationConsultationEntity>(ReservationConsultationTable) {

        /**
         * 예약 상담 row 를 생성하는 팩토리 메서드.
         * 상태 전이/초기 생성을 동일한 방식으로 처리하기 위해 note 는 선택 입력이다.
         *
         * @param reservation 연관 예약 엔티티
         * @param status      기록할 상태
         * @param note        상담 메모 (초기 생성/취소 시에는 null)
         */
        fun create(
            reservation: ReservationEntity,
            status: ReservationStatus,
            note: String? = null,
        ): ReservationConsultationEntity = new(UuidV7.generate()) {
            this.reservation = reservation
            this.status = status
            this.note = note
        }
    }

    var reservation by ReservationEntity referencedOn ReservationConsultationTable.reservationId
    var status by ReservationConsultationTable.status
    var note by ReservationConsultationTable.note
}
