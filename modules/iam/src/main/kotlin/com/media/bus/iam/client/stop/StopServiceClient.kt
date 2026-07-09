package com.media.bus.iam.client.stop

import com.media.bus.iam.client.stop.dto.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.util.*

/**
 * ## stop 서비스 내부 API 클라이언트
 *
 * 설계 의도:
 * - `StopApi`(`@HttpExchange` 프록시)에 HTTP 호출을 위임하고,
 *   응답 변환(`extractList`) 등 비즈니스 로직을 이 클래스에서 처리한다.
 * - 소비자의 공개 API는 변경하지 않는다.
 */
@Component
class StopServiceClient(
    private val stopApi: StopApi,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** pk(UUID) 기준으로 정류소를 조회한다. 존재하지 않으면 빈 리스트를 반환한다. */
    fun getStopByPk(pk: UUID): List<StopInfo> {
        log.debug("[StopServiceClient] pk 기준 정류소 조회: pk={}", pk)
        return extractList(stopApi.getStopByPk(pk))
    }

    /** stopId(정류소 번호) 기준으로 정류소를 조회한다. 존재하지 않으면 빈 리스트를 반환한다. */
    fun getStopByStopId(stopId: String): List<StopInfo> {
        log.debug("[StopServiceClient] stopId 기준 정류소 조회: stopId={}", stopId)
        return extractList(stopApi.getStopByStopId(stopId))
    }

    /** stopName(이름 완전 일치) 기준으로 정류소를 조회한다. 동명 정류소 시 다건 반환. 없으면 빈 리스트. */
    fun getStopByStopName(stopName: String): List<StopInfo> {
        log.debug("[StopServiceClient] stopName 기준 정류소 조회: stopName={}", stopName)
        return extractList(stopApi.getStopByStopName(stopName))
    }

    /**
     * pk(UUID) 복수 기반 일괄 조회. 누락된 id는 응답 리스트에 포함되지 않는다.
     * 빈 입력에 대해서는 S2S 호출 없이 빈 리스트를 반환한다.
     */
    fun getStopsByPks(pks: Collection<UUID>): List<StopInfo> {
        if (pks.isEmpty()) return emptyList()
        log.debug("[StopServiceClient] pk 기준 일괄 정류소 조회: count={}", pks.size)
        return extractList(stopApi.getStopsByPks(BulkStopLookupRequest(ids = pks.toList())))
    }

    private fun extractList(response: StopPageResponse?): List<StopInfo> =
        response?.data?.list ?: emptyList()

    /** 정류소 단가를 조회한다. 등록된 단가가 없으면 null을 반환한다. */
    fun getStopPrice(stopId: UUID): StopPriceInfo? {
        log.debug("[StopServiceClient] 정류소 단가 조회: stopId={}", stopId)
        return stopApi.getStopPrice(stopId)?.data
    }

    /** 정류소 단가를 신규 등록한다. */
    fun createStopPrice(stopId: UUID, unitPrice: BigDecimal, registeredById: UUID): StopPriceInfo {
        log.debug("[StopServiceClient] 정류소 단가 등록: stopId={}", stopId)
        val response = stopApi.createStopPrice(CreateStopPriceRequest(stopId, unitPrice, registeredById))
        return response?.data ?: throw IllegalStateException("정류소 단가 등록에 실패했습니다.")
    }

    /** 정류소 단가를 수정한다. */
    fun updateStopPrice(stopId: UUID, unitPrice: BigDecimal, changedById: UUID): StopPriceInfo {
        log.debug("[StopServiceClient] 정류소 단가 수정: stopId={}", stopId)
        val response = stopApi.updateStopPrice(stopId, UpdateStopPriceRequest(unitPrice, changedById))
        return response?.data ?: throw IllegalStateException("정류소 단가 수정에 실패했습니다.")
    }

    /** 정류소 단가를 삭제한다. */
    fun deleteStopPrice(stopId: UUID, changedById: UUID) {
        log.debug("[StopServiceClient] 정류소 단가 삭제: stopId={}", stopId)
        stopApi.deleteStopPrice(stopId, changedById)
    }
}
