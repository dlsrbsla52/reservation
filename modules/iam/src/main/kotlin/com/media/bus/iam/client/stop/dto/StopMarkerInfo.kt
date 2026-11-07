package com.media.bus.iam.client.stop.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.math.BigDecimal
import java.util.*

/** stop 서비스 지도 마커 한 건의 역직렬화용 DTO. `stopsType`은 모듈 경계상 문자열로 받는다. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class StopMarkerInfo(
    val id: UUID,
    val stopId: String,
    val stopName: String,
    val lat: BigDecimal,
    val lng: BigDecimal,
    val stopsType: String,
)
