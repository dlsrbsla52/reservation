package com.media.bus.iam.client.reservation.dto

import java.math.BigDecimal
import java.time.OffsetDateTime

data class CompleteToContractRequest(
    val contractName: String,
    val totalAmount: BigDecimal,
    val payAmount: BigDecimal?,
    val paymentCycle: String,
    val paymentMethod: String,
    val contractStartDate: OffsetDateTime,
    val contractEndDate: OffsetDateTime,
    val note: String?,
)
