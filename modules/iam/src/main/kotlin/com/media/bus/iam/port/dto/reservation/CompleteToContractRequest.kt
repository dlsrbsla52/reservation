package com.media.bus.iam.port.dto.reservation

import java.math.BigDecimal
import java.time.OffsetDateTime

/** 예약 → 계약 완료 요청 (관리자 API 본문이자 `ReservationAdminPort` 입력) */
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
