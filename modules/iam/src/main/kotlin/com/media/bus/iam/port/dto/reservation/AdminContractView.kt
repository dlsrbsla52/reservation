package com.media.bus.iam.port.dto.reservation

import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.*

/**
 * ## 관리자 계약 한 건 (`ReservationAdminPort` 응답)
 *
 * 모듈 경계 원칙:
 * - reservation 모듈의 DTO/Enum을 직접 참조하지 않는다.
 * - 도메인 Enum(`ContractStatus`, `PaymentCycle` 등)은 모듈 간 결합을 피하기 위해 `String`으로 수신한다.
 */
data class AdminContractView(
    val id: UUID,
    val stopId: UUID,
    val stopNumber: String?,
    val stopName: String?,
    val memberId: UUID?,
    val contractName: String,
    val status: String,
    val contractStartDate: OffsetDateTime,
    val contractEndDate: OffsetDateTime,
    val totalAmount: BigDecimal?,
    val payAmount: BigDecimal?,
    val paymentCycle: String?,
    val paymentMethod: String?,
    val paymentStatus: String?,
    val paidAmount: BigDecimal?,
    val paidAt: OffsetDateTime?,
    val createdAt: OffsetDateTime,
)
