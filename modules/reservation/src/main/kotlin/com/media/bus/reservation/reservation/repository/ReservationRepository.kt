package com.media.bus.reservation.reservation.repository

import com.media.bus.reservation.reservation.entity.ReservationEntity
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.*

/**
 * ## 예약 저장소
 *
 * Exposed DAO 기반 예약 조회 메서드 제공.
 */
@Repository
class ReservationRepository {

    @Transactional(readOnly = true)
    fun findById(id: UUID): ReservationEntity? = ReservationEntity.findById(id)
}
