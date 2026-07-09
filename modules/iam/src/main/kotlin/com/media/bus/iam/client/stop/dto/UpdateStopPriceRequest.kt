package com.media.bus.iam.client.stop.dto

import java.math.BigDecimal
import java.util.*

data class UpdateStopPriceRequest(
    val unitPrice: BigDecimal,
    val changedById: UUID?,
)
