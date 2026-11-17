package com.media.bus.iam.port.dto.stop

/**
 * ## 지도 영역 정류소 마커 조회 결과 (`StopAdminPort` 응답)
 *
 * stop 모듈의 `StopBoundsResponse`를 iam 쪽 타입으로 옮긴 것이다. 모듈 경계상 stop 클래스를 참조하지 않는다.
 */
data class StopBoundsInfo(
    val items: List<StopMarkerInfo>?,
    val truncated: Boolean?,
    val limit: Int?,
)
