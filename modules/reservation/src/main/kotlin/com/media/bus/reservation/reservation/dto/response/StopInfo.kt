package com.media.bus.reservation.reservation.dto.response

import java.util.*

/** 정류소 정보의 reservation 내부 표현 (`StopLookupPort` 응답) */
data class StopInfo(
    val id: UUID,
    val stopId: String,
    val stopName: String,
)
