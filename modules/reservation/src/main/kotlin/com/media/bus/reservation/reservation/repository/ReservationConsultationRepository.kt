package com.media.bus.reservation.reservation.repository

import com.media.bus.reservation.reservation.entity.ReservationConsultationEntity
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.*

/**
 * ## 예약 상담 저장소
 *
 * Exposed DAO 기반 예약 상담 조회 메서드 제공.
 */
@Repository
class ReservationConsultationRepository {

    @Transactional(readOnly = true)
    fun findById(id: UUID): ReservationConsultationEntity? = ReservationConsultationEntity.findById(id)
}
