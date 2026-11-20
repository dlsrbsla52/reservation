package com.media.bus.stop.map.dto

import io.swagger.v3.oas.annotations.media.Schema

/** 지도 영역 조회 응답 */
@Schema(name = "StopMapBoundsResponse", description = "지도 영역 내 정류소 마커 목록")
data class StopBoundsResponse(
    @get:Schema(description = "영역에 포함된 정류소 마커 목록")
    val items: List<StopMarkerResponse>,

    @get:Schema(description = "상한 초과로 결과가 잘렸는지 여부", example = "false")
    val truncated: Boolean,

    @get:Schema(description = "적용된 반환 상한 건수", example = "1000")
    val limit: Int,
)
