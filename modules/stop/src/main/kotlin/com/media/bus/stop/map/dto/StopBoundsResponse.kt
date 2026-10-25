package com.media.bus.stop.map.dto

import io.swagger.v3.oas.annotations.media.Schema

/**
 * ## 지도 영역 조회 응답
 *
 * `truncated` 가 true 이면 요청 영역에 상한을 넘는 정류소가 존재해 일부만 반환된 것이다.
 * 프론트는 이 플래그를 보고 "확대해서 보세요" 안내를 띄우거나 클러스터 단위 표시로 전환한다.
 * 잘렸다는 사실을 알리지 않으면 사용자는 지도에 없는 정류소를 "데이터가 없다"고 오인하게 된다.
 */
@Schema(description = "지도 영역 내 정류소 마커 목록")
data class StopBoundsResponse(
    @get:Schema(description = "영역에 포함된 정류소 마커 목록")
    val items: List<StopMarkerResponse>,

    @get:Schema(description = "상한 초과로 결과가 잘렸는지 여부", example = "false")
    val truncated: Boolean,

    @get:Schema(description = "적용된 반환 상한 건수", example = "1000")
    val limit: Int,
)
