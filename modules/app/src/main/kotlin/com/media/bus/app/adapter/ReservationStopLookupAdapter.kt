package com.media.bus.app.adapter

import com.media.bus.reservation.reservation.dto.response.StopInfo
import com.media.bus.reservation.reservation.port.StopLookupPort
import com.media.bus.stop.dto.request.StopSearchCriteria
import com.media.bus.stop.dto.response.BusStopResponse
import com.media.bus.stop.service.StopService
import org.springframework.stereotype.Component
import java.util.*

/** reservation → stop 정류소 조회 어댑터 */
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
        if (pks.isEmpty()) return emptyList()
        return callProvider { stopService.getBusStopsByIds(pks).map(::toStopInfo) }
    }

    private fun toStopInfo(stop: BusStopResponse) = StopInfo(id = stop.id, stopId = stop.stopId, stopName = stop.stopName, unitPrice = stop.unitPrice)
}
