package com.media.bus.reservation.reservation.entity

import com.media.bus.common.entity.common.DateBaseEntity
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.java.UUIDEntityClass
import java.util.*

/**
 * ## 예약 상담 Exposed DAO 엔티티
 *
 * 예약별 상담 이력을 기록하는 엔티티.
 */
class ReservationConsultationEntity(id: EntityID<UUID>) : DateBaseEntity(id, ReservationConsultationTable) {

    companion object : UUIDEntityClass<ReservationConsultationEntity>(ReservationConsultationTable)

    var reservation by ReservationEntity referencedOn ReservationConsultationTable.reservationId
    var status by ReservationConsultationTable.status
    var note by ReservationConsultationTable.note
}
