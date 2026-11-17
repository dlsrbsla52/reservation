package com.media.bus.iam.port.dto.stop

import java.math.BigDecimal
import java.util.*

/** 지도 마커 한 건 (`StopAdminPort` 응답). `stopsType`은 모듈 경계상 enum 이름 문자열로 받는다. */
data class StopMarkerInfo(
    val id: UUID,
    val stopId: String,
    val stopName: String,
    val lat: BigDecimal,
    val lng: BigDecimal,
    val stopsType: String,
)
