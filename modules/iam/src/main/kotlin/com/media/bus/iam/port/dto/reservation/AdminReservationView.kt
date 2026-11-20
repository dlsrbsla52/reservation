package com.media.bus.iam.port.dto.reservation

import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

/** ## 관리자 예약 목록의 한 행 (`ReservationAdminPort` 응답). 상태 enum은 모듈 경계상 이름 문자열로 받는다. */
data class AdminReservationView(
    val reservationId: UUID,
    val memberId: UUID,
    val stopId: UUID,
    val stopNumber: String?,
    val stopName: String?,
    val status: String,
    val holdExpiresAt: OffsetDateTime?,
    val assigneeId: UUID?,
    val consultationRequestedAt: OffsetDateTime,
    val desiredContractStartDate: LocalDate,
    val createdAt: OffsetDateTime,
)
