package com.media.bus.iam.port.dto.stop

import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.*

/** 정류소 단가 (`StopAdminPort` 응답). */
data class StopPriceInfo(
    val id: UUID,
    val stopId: UUID,
    val unitPrice: BigDecimal,
    val registeredById: UUID?,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
)
