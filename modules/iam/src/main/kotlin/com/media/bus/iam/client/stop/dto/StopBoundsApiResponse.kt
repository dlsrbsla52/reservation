package com.media.bus.iam.client.stop.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/** stop 서비스 지도 영역 조회 API(`/map/bounds`)의 역직렬화용 DTO */
@JsonIgnoreProperties(ignoreUnknown = true)
data class StopBoundsApiResponse(val data: StopBoundsInfo?) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class StopBoundsInfo(
        val items: List<StopMarkerInfo>?,
        val truncated: Boolean?,
        val limit: Int?,
    )
}
