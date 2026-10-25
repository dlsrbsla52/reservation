package com.media.bus.stop.support

import com.media.bus.common.exceptions.BusinessException
import org.springframework.http.HttpStatus
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * ## WGS84 좌표 파싱·정규화 유틸
 *
 * 공공 API(서울 열린데이터광장)와 수기 등록 요청은 좌표를 **문자열**로 전달하지만,
 * DB 컬럼과 도메인 모델은 `NUMERIC(10, 7)` / [BigDecimal] 이다.
 * 이 경계 변환을 한 곳에 모아 다음 두 가지를 보장한다.
 *
 * 1. **유효성** — 숫자로 파싱되지 않거나 위경도 범위를 벗어난 값은 저장 자체를 거부한다.
 * 2. **정규화** — 항상 scale 을 [SCALE] 로 고정한다.
 *    [BigDecimal.equals] 는 scale 까지 비교하므로(`1.0 != 1.00`), 정규화하지 않으면
 *    DB에서 읽은 값(scale 7 고정)과 새로 파싱한 값의 비교가 어긋나
 *    공공 API 재동기화 때 변경이 없는 정류소를 매번 "변경됨"으로 오탐지하게 된다.
 */
object Coordinate {

    /** DB 컬럼 `NUMERIC(10, 7)` 의 소수부 자릿수. 약 1.1cm 해상도. */
    const val SCALE: Int = 7

    private val LONGITUDE_MIN = BigDecimal("-180")
    private val LONGITUDE_MAX = BigDecimal("180")
    private val LATITUDE_MIN = BigDecimal("-90")
    private val LATITUDE_MAX = BigDecimal("90")

    /**
     * 경도 문자열을 파싱한다. 실패 시 [BusinessException] 을 던진다.
     *
     * @throws BusinessException 숫자 형식이 아니거나 -180 ~ 180 범위를 벗어난 경우
     */
    fun parseLongitude(raw: String): BigDecimal =
        parseOrNullLongitude(raw)
            ?: throw BusinessException(HttpStatus.BAD_REQUEST, "경도(xCrd) 값이 올바르지 않습니다: $raw")

    /**
     * 위도 문자열을 파싱한다. 실패 시 [BusinessException] 을 던진다.
     *
     * @throws BusinessException 숫자 형식이 아니거나 -90 ~ 90 범위를 벗어난 경우
     */
    fun parseLatitude(raw: String): BigDecimal =
        parseOrNullLatitude(raw)
            ?: throw BusinessException(HttpStatus.BAD_REQUEST, "위도(yCrd) 값이 올바르지 않습니다: $raw")

    /**
     * 경도 문자열을 파싱하되 실패 시 `null` 을 반환한다.
     *
     * 공공 API 일괄 적재처럼 **한 건의 불량 데이터가 전체 배치를 중단시키면 안 되는** 경로에서 사용한다.
     */
    fun parseOrNullLongitude(raw: String?): BigDecimal? =
        parseInRange(raw, LONGITUDE_MIN, LONGITUDE_MAX)

    /**
     * 위도 문자열을 파싱하되 실패 시 `null` 을 반환한다.
     *
     * 공공 API 일괄 적재처럼 **한 건의 불량 데이터가 전체 배치를 중단시키면 안 되는** 경로에서 사용한다.
     */
    fun parseOrNullLatitude(raw: String?): BigDecimal? =
        parseInRange(raw, LATITUDE_MIN, LATITUDE_MAX)

    /** 이미 [BigDecimal] 인 값의 scale 을 [SCALE] 로 맞춘다. */
    fun normalize(value: BigDecimal): BigDecimal = value.setScale(SCALE, RoundingMode.HALF_UP)

    /**
     * scale 차이를 무시하고 값이 같은지 비교한다.
     *
     * [BigDecimal.equals] 대신 반드시 이 함수를 사용할 것 — `equals` 는 scale 이 다르면 false 다.
     */
    fun isSame(left: BigDecimal?, right: BigDecimal?): Boolean = when {
        left == null && right == null -> true
        left == null || right == null -> false
        else                          -> left.compareTo(right) == 0
    }

    private fun parseInRange(raw: String?, min: BigDecimal, max: BigDecimal): BigDecimal? {
        val trimmed = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        // BigDecimal 은 "1e2" 같은 지수 표기도 받아들이므로 파싱 후 범위 검증이 반드시 필요하다
        val parsed = trimmed.toBigDecimalOrNull() ?: return null
        if (parsed < min || parsed > max) return null
        return normalize(parsed)
    }
}
