package com.media.bus.stop.support

import com.media.bus.common.exceptions.BusinessException
import com.media.bus.stop.result.StopResult
import java.math.BigDecimal
import java.math.RoundingMode

/** WGS84 좌표 파싱·정규화 유틸 */
object Coordinate {

    const val SCALE: Int = 7

    private val LONGITUDE_MIN = BigDecimal("-180")
    private val LONGITUDE_MAX = BigDecimal("180")
    private val LATITUDE_MIN = BigDecimal("-90")
    private val LATITUDE_MAX = BigDecimal("90")

    /** 경도 문자열을 파싱한다. */
    fun parseLongitude(raw: String): BigDecimal =
        parseOrNullLongitude(raw)
            ?: throw BusinessException(StopResult.INVALID_LONGITUDE)

    /** 위도 문자열을 파싱한다. */
    fun parseLatitude(raw: String): BigDecimal =
        parseOrNullLatitude(raw)
            ?: throw BusinessException(StopResult.INVALID_LATITUDE)

    /** 경도 문자열을 파싱하되 실패 시 `null` 을 반환한다. */
    fun parseOrNullLongitude(raw: String?): BigDecimal? =
        parseInRange(raw, LONGITUDE_MIN, LONGITUDE_MAX)

    /** 위도 문자열을 파싱하되 실패 시 `null` 을 반환한다. */
    fun parseOrNullLatitude(raw: String?): BigDecimal? =
        parseInRange(raw, LATITUDE_MIN, LATITUDE_MAX)

    /** 이미 [BigDecimal] 인 값의 scale 을 [SCALE] 로 맞춘다. */
    fun normalize(value: BigDecimal): BigDecimal = value.setScale(SCALE, RoundingMode.HALF_UP)

    /** scale 차이를 무시하고 값이 같은지 비교한다. */
    fun isSame(left: BigDecimal?, right: BigDecimal?): Boolean = when {
        left == null && right == null -> true
        left == null || right == null -> false
        else -> left.compareTo(right) == 0
    }

    private fun parseInRange(raw: String?, min: BigDecimal, max: BigDecimal): BigDecimal? {
        val trimmed = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val parsed = trimmed.toBigDecimalOrNull() ?: return null
        if (parsed < min || parsed > max) return null
        return normalize(parsed)
    }
}
