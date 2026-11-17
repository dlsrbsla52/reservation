package com.media.bus.iam.port.dto.reservation

import java.time.OffsetDateTime
import java.util.*

/** 관리자 계약 생성 결과 (`ReservationAdminPort` 응답). */
data class ContractView(
    val id: UUID,
    val stopId: UUID,
    val stopNumber: String?,
    val stopName: String?,
    val memberId: UUID?,
    val contractName: String,
    val status: String,
    val contractStartDate: OffsetDateTime,
    val contractEndDate: OffsetDateTime,
    val createdAt: OffsetDateTime,
)
