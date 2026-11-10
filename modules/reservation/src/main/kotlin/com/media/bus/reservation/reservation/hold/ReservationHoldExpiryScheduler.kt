package com.media.bus.reservation.reservation.hold

import com.media.bus.reservation.reservation.service.ReservationFacade
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * ## 선점 만료 스케줄러
 *
 * 선점 기간(`hold_expires_at`)이 지난 예약을 주기적으로 `EXPIRED` 로 종료해 광고면을 푼다.
 *
 * 이 스케줄러가 늦거나 멈춰도 선착순 판정은 틀어지지 않는다 — 예약 생성 시점에도 만료된 선점을
 * 직접 확인해 정리하기 때문이다. 스케줄러의 역할은 "아무도 다시 예약하지 않는 광고면"의 상태를
 * 제때 `EXPIRED` 로 맞춰 목록과 이력이 사실과 같게 하는 것이다.
 *
 * 인스턴스가 여러 대여도 안전하다. 대상 조회가 `FOR UPDATE SKIP LOCKED` 라 같은 예약을 두 번 만료시키지 않는다.
 *
 * `reservation.hold.expiry-enabled=false` 로 끌 수 있다(스키마 없이 뜨는 OpenAPI 문서 생성 프로파일용).
 */
@Component
@ConditionalOnProperty(prefix = "reservation.hold", name = ["expiry-enabled"], havingValue = "true", matchIfMissing = true)
class ReservationHoldExpiryScheduler(
    private val reservationFacade: ReservationFacade,
    private val properties: HoldProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        /** 한 주기에 처리할 최대 배치 수. 대상이 계속 쌓여도 한 주기가 끝없이 돌지 않게 한다. */
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
            // 다음 주기에 다시 시도한다. 여기서 예외가 새면 스케줄러 스레드 로그만 남고 원인을 놓치기 쉽다.
            log.error("[ReservationHoldExpiryScheduler] 선점 만료 처리 실패", e)
        }
    }
}
