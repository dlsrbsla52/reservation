package com.media.bus.iam.port.dto.stop

/** 지도 영역 정류소 마커 조회 결과 (`StopAdminPort` 응답) */
data class StopBoundsInfo(
    val items: List<StopMarkerInfo>?,
    val truncated: Boolean?,
    val limit: Int?,
)
