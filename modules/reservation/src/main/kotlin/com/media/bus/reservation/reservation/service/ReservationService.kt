package com.media.bus.reservation.reservation.service

import com.media.bus.reservation.reservation.repository.ReservationRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class ReservationService(
    private val reservationRepository: ReservationRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // TODO : 추후 구현 필요
    fun existsReservation() {
    }
}
