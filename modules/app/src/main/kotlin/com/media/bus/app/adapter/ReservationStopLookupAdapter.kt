package com.media.bus.app.adapter

import com.media.bus.reservation.reservation.dto.response.StopInfo
import com.media.bus.reservation.reservation.port.StopLookupPort
import com.media.bus.stop.dto.request.StopSearchCriteria
import com.media.bus.stop.dto.response.BusStopResponse
import com.media.bus.stop.service.StopService
import org.springframework.stereotype.Component
import java.util.*

/**
 * ## reservation → stop 정류소 조회 어댑터
 *
 * 예전 `GET /api/v1/internal/stop`, `POST /api/v1/internal/stop/bulk`가 위임하던 `StopService`를 직접 부른다.
 * 트랜잭션은 `StopService`의 `@Transactional(readOnly = true)`가 연다. 어댑터에는 트랜잭션을 두지 않는다.
 */
@Component
class ReservationStopLookupAdapter(
    private val stopService: StopService,
) : StopLookupPort {

    override fun getStopByPk(pk: Set<UUID>): List<StopInfo> = callProvider {
        stopService.getBusStop(StopSearchCriteria.of(pk, null, null)).map(::toStopInfo)
    }

    override fun getStopByStopId(stopId: String): List<StopInfo> = callProvider {
        stopService.getBusStop(StopSearchCriteria.of(null, stopId, null)).map(::toStopInfo)
    }

    override fun getStopsByPks(pks: Collection<UUID>): List<StopInfo> {
        // 빈 입력은 호출하지 않는다. 제공자는 빈 목록을 400으로 거절한다(예전 HTTP 클라이언트와 같은 동작).
        if (pks.isEmpty()) return emptyList()
        return callProvider { stopService.getBusStopsByIds(pks).map(::toStopInfo) }
    }

    private fun toStopInfo(stop: BusStopResponse) = StopInfo(id = stop.id, stopId = stop.stopId, stopName = stop.stopName)
}
