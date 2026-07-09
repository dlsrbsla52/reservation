package com.media.bus.iam.client.stop.dto

import java.math.BigDecimal
import java.util.*

data class CreateStopPriceRequest(
    val stopId: UUID,
    val unitPrice: BigDecimal,
    val registeredById: UUID?,
)
