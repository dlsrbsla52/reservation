package com.media.bus.stop.map.dto

import com.media.bus.common.exceptions.BusinessException
import com.media.bus.stop.entity.enums.StopType
import com.media.bus.stop.support.Coordinate
import org.springframework.http.HttpStatus
import java.math.BigDecimal

/**
 * ## 지도 영역(bounding box) 조회 기준
 *
 * 카카오맵 `map.getBounds()` 가 돌려주는 남서(sw) / 북동(ne) 두 꼭짓점을 표현한다.
 * 쿼리 파라미터를 그대로 Repository 로 흘리지 않고 이 값 객체에서 한 번에 검증해,
 * 잘못된 범위가 DB 풀스캔으로 이어지는 것을 막는다.
 *
 * @property swLat 남서쪽 위도 (y_crd 하한)
 * @property swLng 남서쪽 경도 (x_crd 하한)
 * @property neLat 북동쪽 위도 (y_crd 상한)
 * @property neLng 북동쪽 경도 (x_crd 상한)
 * @property limit 반환 상한 건수
 * @property stopsType 정류소 유형 필터. null 이면 전체 유형
 */
data class StopBoundsCriteria(
    val swLat: BigDecimal,
    val swLng: BigDecimal,
    val neLat: BigDecimal,
    val neLng: BigDecimal,
    val limit: Int,
    val stopsType: StopType? = null,
) {
    companion object {

        /** 프론트가 limit 을 지정하지 않았을 때의 기본 상한. 마커 클러스터링 기준 안전값. */
        const val DEFAULT_LIMIT: Int = 1_000

        /** 클라이언트가 요청할 수 있는 최대 상한. 이 값을 넘기면 조용히 잘라낸다(예외 아님). */
        const val MAX_LIMIT: Int = 2_000

        /**
         * 쿼리 파라미터를 검증된 조회 기준으로 변환한다.
         *
         * 검증 항목:
         * 1. 각 좌표가 숫자이며 위경도 유효 범위 안에 있을 것
         * 2. 남서 좌표가 북동 좌표보다 크지 않을 것
         *
         * **날짜변경선(antimeridian) 교차 미지원** — `swLng > neLng` 인 영역은 400으로 거부한다.
         * 서울시 정류소만 다루는 현 서비스 범위에서는 발생하지 않으며,
         * 지원하려면 경도 조건을 OR 두 구간으로 쪼개야 해서 인덱스 사용 계획이 달라진다.
         *
         * `stopsType` 은 선택 필터다. 값이 없으면 전체 유형을 조회하고,
         * 알 수 없는 값이면 [StopType.parseFilter] 가 400 을 던진다.
         *
         * @throws BusinessException 검증 실패 시 400 BAD_REQUEST
         */
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
                throw BusinessException(HttpStatus.BAD_REQUEST, "swLat은 neLat보다 클 수 없습니다.")
            }
            if (parsedSwLng > parsedNeLng) {
                throw BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "swLng은 neLng보다 클 수 없습니다. (날짜변경선을 넘는 영역은 지원하지 않습니다)",
                )
            }

            return StopBoundsCriteria(
                swLat = parsedSwLat,
                swLng = parsedSwLng,
                neLat = parsedNeLat,
                neLng = parsedNeLng,
                // 0 이하 요청은 기본값으로, 과도한 요청은 상한으로 보정한다
                limit = (limit?.takeIf { it > 0 } ?: DEFAULT_LIMIT).coerceAtMost(MAX_LIMIT),
                stopsType = StopType.parseFilter(stopsType),
            )
        }
    }
}
