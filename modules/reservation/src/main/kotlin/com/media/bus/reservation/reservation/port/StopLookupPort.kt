package com.media.bus.reservation.reservation.port

import com.media.bus.reservation.reservation.dto.response.StopInfo
import java.util.*

/** 예약·계약 흐름이 쓰는 정류소 조회 포트 */
interface StopLookupPort {

    /** pk(UUID) 기준으로 정류소를 조회한다. 없으면 빈 리스트. */
    fun getStopByPk(pk: Set<UUID>): List<StopInfo>

    /** stopId(정류소 번호) 기준으로 정류소를 조회한다. 없으면 빈 리스트. */
    fun getStopByStopId(stopId: String): List<StopInfo>

    /** pk(UUID) 복수 일괄 조회. 누락된 id는 결과에 포함되지 않고, 빈 입력이면 빈 리스트. */
    fun getStopsByPks(pks: Collection<UUID>): List<StopInfo>
}
