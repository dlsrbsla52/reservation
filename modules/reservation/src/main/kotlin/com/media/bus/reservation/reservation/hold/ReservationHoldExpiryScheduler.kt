package com.media.bus.reservation.reservation.hold

import com.media.bus.reservation.reservation.service.ReservationFacade
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/** 선점 만료 스케줄러 */
@Component
@ConditionalOnProperty(prefix = "reservation.hold", name = ["expiry-enabled"], havingValue = "true", matchIfMissing = true)
class ReservationHoldExpiryScheduler(
    private val reservationFacade: ReservationFacade,
    private val properties: HoldProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val MAX_BATCHES_PER_RUN = 20
    }

    @Scheduled(
        fixedDelayString = "\${reservation.hold.expiry-interval:PT1M}",
        initialDelayString = "\${reservation.hold.expiry-initial-delay:PT30S}",
    )
    fun expireOverdueHolds() {
        try {
            var batches = 0
            do {
                val expired = reservationFacade.expireOverdueHolds()
                batches++
            } while (expired >= properties.expiryBatchSize && batches < MAX_BATCHES_PER_RUN)
        } catch (e: Exception) {
            log.error("[ReservationHoldExpiryScheduler] 선점 만료 처리 실패", e)
        }
    }
}
