package com.media.bus.iam.port.dto.reservation

import java.math.BigDecimal
import java.time.OffsetDateTime

/**
 * ## 예약 → 계약 완료 요청 (관리자 API 본문이자 `ReservationAdminPort` 입력)
 *
 * 코드값(`paymentCycle`·`paymentMethod`)은 문자열이고 enum 변환은 `app` 어댑터가 한다(알 수 없는 값 → 400).
 */
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
