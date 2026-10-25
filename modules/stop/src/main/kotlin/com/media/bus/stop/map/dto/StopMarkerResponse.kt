package com.media.bus.stop.map.dto

import com.media.bus.stop.entity.enums.StopType
import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.util.*

/**
 * ## 지도 마커 전용 경량 응답 DTO
 *
 * 정류소 한 건당 마커를 찍는 데 필요한 최소 필드만 담는다.
 * `BusStopResponse` 를 재사용하지 않는 이유:
 * - 생성일시·수정일시·단가·노드ID 등은 지도에 불필요한데, 수천 건 × 불필요 필드는 페이로드를 크게 키운다
 * - 좌표 필드명이 `xCrd`/`yCrd` 라서 프론트에서 `(lat, lng)` 순서를 헷갈리기 쉽다
 *
 * 필드명을 `lat`/`lng` 로 고정한 것은 카카오맵 `new kakao.maps.LatLng(lat, lng)` 인자 순서와
 * 1:1로 대응시켜 좌표 뒤바뀜 사고를 원천 차단하기 위함이다.
 * 또한 게터명이 `getLat()`/`getLng()` 이라 Jackson 기본 네이밍에서도 직렬화명이 그대로 유지된다
 * (`xCrd` → `getXCrd()` → `xcrd` 로 깨지던 문제가 발생하지 않는다).
 */
@Schema(description = "지도 마커용 정류소 좌표")
data class StopMarkerResponse(
    @get:Schema(description = "정류소 PK (UUID)", example = "ac130005-9cf0-14ae-819c-f08586140000")
    val id: UUID,

    @get:Schema(description = "정류소 번호 (STOPS_NO)", example = "11833")
    val stopId: String,

    @get:Schema(description = "정류소 이름", example = "중계역")
    val stopName: String,

    @get:Schema(description = "위도 WGS84 (y_crd)", example = "37.6472686")
    val lat: BigDecimal,

    @get:Schema(description = "경도 WGS84 (x_crd)", example = "127.0627647")
    val lng: BigDecimal,

    /**
     * 지도에서 유형별로 마커를 걸러내려면 클라이언트가 각 마커의 유형을 알아야 한다.
     * 단가와 달리 정류소 테이블의 자체 컬럼이라 조인이 없고 페이로드 증가도 문자열 한 개뿐이다.
     */
    @get:Schema(description = "정류소 유형", example = "VILLAGE_BUS")
    val stopsType: StopType,
)
