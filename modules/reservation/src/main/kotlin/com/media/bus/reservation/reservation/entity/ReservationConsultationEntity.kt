package com.media.bus.reservation.reservation.entity

import com.media.bus.common.entity.common.DateBaseEntity
import com.media.bus.common.entity.common.UuidV7
import com.media.bus.reservation.reservation.entity.enums.ReservationStatus
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.java.UUIDEntityClass
import java.util.*

/** 예약 상담 Exposed DAO 엔티티 */
class ReservationConsultationEntity(id: EntityID<UUID>) : DateBaseEntity(id, ReservationConsultationTable) {

    companion object : UUIDEntityClass<ReservationConsultationEntity>(ReservationConsultationTable) {

        /** 예약 상담 row 를 생성하는 팩토리 메서드. */
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
