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
}
