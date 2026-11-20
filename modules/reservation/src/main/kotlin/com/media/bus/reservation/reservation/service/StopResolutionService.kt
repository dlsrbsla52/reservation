package com.media.bus.reservation.reservation.service

import com.media.bus.common.exceptions.StorageException
import com.media.bus.reservation.reservation.dto.response.StopInfo
import com.media.bus.reservation.reservation.port.StopLookupPort
import com.media.bus.reservation.reservation.result.ReservationResult
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.*

/** stop 모듈과의 연동을 담당하는 서비스 */
@Service
class StopResolutionService(
    private val stopLookupPort: StopLookupPort,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** stopId(UUID pk)로 정류소를 조회하고 반환한다. */
    fun resolveStop(stopId: UUID): StopInfo =
        stopLookupPort.getStopByPk(setOf(stopId)).firstOrNull()
            ?: run {
                log.warn("[StopResolutionService] 존재하지 않는 정류소: stopId={}", stopId)
                throw StorageException(ReservationResult.STOP_NOT_FOUND)
            }

    /** 복수 stopId를 한 번의 포트 호출로 조회한다. */
    fun resolveStops(stopIds: Collection<UUID>): Map<UUID, StopInfo> {
        if (stopIds.isEmpty()) return emptyMap()
        return try {
            stopLookupPort.getStopsByPks(stopIds).associateBy { it.id }
        } catch (e: Exception) {
            log.warn(
                "[StopResolutionService] 일괄 정류소 조회 실패 — 빈 맵으로 fallback. requested={}, cause={}",
                stopIds.size, e.message,
            )
            emptyMap()
        }
    }
}
