package com.media.bus.reservation.reservation.repository

import com.media.bus.reservation.reservation.entity.ReservationConsultationEntity
import com.media.bus.reservation.reservation.entity.ReservationConsultationTable
import com.media.bus.reservation.reservation.entity.ReservationEntity
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.*

/**
 * ## 예약 상담 저장소
 *
 * 상담 이력은 append-only 로 저장되므로 "최신 row = 현재 상태" 로 해석한다.
 */
@Repository
class ReservationConsultationRepository {

    @Transactional(readOnly = true)
    fun findById(id: UUID): ReservationConsultationEntity? = ReservationConsultationEntity.findById(id)

    /** 예약의 상담 이력 전체 (생성일 오름차순). 상세 조회 시 타임라인 표시용. */
    @Transactional(readOnly = true)
    fun findAllByReservation(reservation: ReservationEntity): List<ReservationConsultationEntity> =
        ReservationConsultationEntity.find {
            ReservationConsultationTable.reservationId eq reservation.id
        }
            .orderBy(ReservationConsultationTable.createdAt to SortOrder.ASC)
            .toList()

    /** 예약의 현재 상태를 결정하는 최신 상담 row 조회. */
    @Transactional(readOnly = true)
    fun findLatestByReservation(reservation: ReservationEntity): ReservationConsultationEntity? =
        ReservationConsultationEntity.find {
            ReservationConsultationTable.reservationId eq reservation.id
        }
            .orderBy(ReservationConsultationTable.createdAt to SortOrder.DESC)
            .limit(1)
            .firstOrNull()
}
