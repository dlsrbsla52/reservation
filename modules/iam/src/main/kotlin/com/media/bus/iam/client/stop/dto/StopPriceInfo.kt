package com.media.bus.iam.client.stop.dto

import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.*

data class StopPriceInfo(
    val id: UUID,
    val stopId: UUID,
    val unitPrice: BigDecimal,
    val registeredById: UUID?,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
)
