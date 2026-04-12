package com.media.bus.reservation.reservation.service

import com.media.bus.common.exceptions.StorageException
import com.media.bus.reservation.reservation.client.StopServiceClient
import com.media.bus.reservation.reservation.dto.response.StopInfo
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.*

/**
 * ## stop 서비스와의 연동을 담당하는 서비스
 *
 * Facade에서 직접 `StopServiceClient`를 호출하지 않고 이 서비스를 경유한다.
 * - 정류소 존재 여부 검증, 예외 변환 등 stop 관련 비즈니스 규칙을 이곳에서 처리한다.
 * - stop 서비스 응답 구조 변경 시 이 클래스만 수정한다 (Facade 변경 불필요).
 */
@Service
class StopResolutionService(
    private val stopServiceClient: StopServiceClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * stopId(UUID pk)로 정류소를 조회하고 반환한다.
     * 존재하지 않으면 `StorageException`(404)을 던진다.
     *
     * @param stopId 예약 요청의 stopId (stop 테이블 UUID pk)
     * @return 조회된 정류소 정보
     * @throws StorageException 정류소를 찾을 수 없는 경우
     */
    fun resolveStop(stopId: UUID): StopInfo =
        stopServiceClient.getStopByPk(stopId).firstOrNull()
            ?: run {
                log.warn("[StopResolutionService] 존재하지 않는 정류소: stopId={}", stopId)
                throw StorageException(message = "요청한 정류소를 찾을 수 없습니다. stopId=$stopId")
            }

    /**
     * 복수 stopId를 한 번의 S2S 호출로 조회한다.
     *
     * **Fallback 정책** — stop 서비스 장애 시 예약 목록 조회 자체가 막히지 않도록
     * 예외를 삼키고 빈 맵을 반환한다. 호출부는 값이 없는 경우 stopName/stopNumber를 null로 노출한다.
     *
     * @return stopId → StopInfo 매핑. 누락되거나 서비스 장애 시 빈 맵.
     */
    fun resolveStops(stopIds: Collection<UUID>): Map<UUID, StopInfo> {
        if (stopIds.isEmpty()) return emptyMap()
        return try {
            stopServiceClient.getStopsByPks(stopIds).associateBy { it.id }
        } catch (e: Exception) {
            log.warn(
                "[StopResolutionService] 일괄 정류소 조회 실패 — 빈 맵으로 fallback. requested={}, cause={}",
                stopIds.size, e.message,
            )
            emptyMap()
        }
    }
}
