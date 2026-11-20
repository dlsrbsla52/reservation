package com.media.bus.stop.map.dto

import com.media.bus.common.exceptions.BusinessException
import com.media.bus.stop.entity.enums.StopType
import com.media.bus.stop.result.StopResult
import com.media.bus.stop.support.Coordinate
import java.math.BigDecimal

/** 지도 영역(bounding box) 조회 기준 */
data class StopBoundsCriteria(
    val swLat: BigDecimal,
    val swLng: BigDecimal,
    val neLat: BigDecimal,
    val neLng: BigDecimal,
    val limit: Int,
    val stopsType: StopType? = null,
) {
    companion object {

        const val DEFAULT_LIMIT: Int = 1_000

        const val MAX_LIMIT: Int = 2_000

        /** 쿼리 파라미터를 검증된 조회 기준으로 변환한다. */
        fun of(
            swLat: String,
            swLng: String,
            neLat: String,
            neLng: String,
            limit: Int?,
            stopsType: String? = null,
        ): StopBoundsCriteria {
            val parsedSwLat = Coordinate.parseLatitude(swLat)
            val parsedSwLng = Coordinate.parseLongitude(swLng)
            val parsedNeLat = Coordinate.parseLatitude(neLat)
            val parsedNeLng = Coordinate.parseLongitude(neLng)

            if (parsedSwLat > parsedNeLat) {
                throw BusinessException(StopResult.INVALID_BOUNDS_LATITUDE)
            }
            if (parsedSwLng > parsedNeLng) {
                throw BusinessException(StopResult.INVALID_BOUNDS_LONGITUDE)
            }

            return StopBoundsCriteria(
                swLat = parsedSwLat,
                swLng = parsedSwLng,
                neLat = parsedNeLat,
                neLng = parsedNeLng,
                limit = (limit?.takeIf { it > 0 } ?: DEFAULT_LIMIT).coerceAtMost(MAX_LIMIT),
                stopsType = StopType.parseFilter(stopsType),
            )
        }
    }
}
