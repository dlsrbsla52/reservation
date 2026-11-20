package com.media.bus.iam.port.dto.reservation

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

/** 관리자 예약 상세 (`ReservationAdminPort` 응답이자 관리자 API 응답) */
data class AdminReservationDetail(
    val reservationId: UUID,
    val memberId: UUID,
    val stopId: UUID,
    val stopNumber: String?,
    val stopName: String?,
    val status: String,
    val holdExpiresAt: OffsetDateTime?,
    val consultationRequestedAt: OffsetDateTime,
    val desiredContractStartDate: LocalDate,
    val createdAt: OffsetDateTime,
    val assigneeId: UUID?,
    val consultations: List<ConsultationHistory>?,
) {
    @Schema(name = "AdminConsultationHistory")
    data class ConsultationHistory(
        val consultationId: UUID,
        val status: String,
        val note: String?,
        val createdAt: OffsetDateTime,
    )
}
