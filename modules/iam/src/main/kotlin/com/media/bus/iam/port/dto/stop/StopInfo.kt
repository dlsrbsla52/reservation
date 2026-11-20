package com.media.bus.iam.port.dto.stop

import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.*

/** 정류소 정보 (`StopAdminPort` 응답) */
data class StopInfo(
    val id: UUID,
    val stopId: String,
    val stopName: String,
    val xCrd: String,
    val yCrd: String,
    val nodeId: String,
    val stopsType: String,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
    val unitPrice: BigDecimal?,
)
