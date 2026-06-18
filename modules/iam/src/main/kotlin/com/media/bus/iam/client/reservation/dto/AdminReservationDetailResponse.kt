package com.media.bus.iam.client.reservation.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

@JsonIgnoreProperties(ignoreUnknown = true)
data class AdminReservationDetailResponse(val data: AdminReservationDetail?) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class AdminReservationDetail(
        val reservationId: UUID,
        val memberId: UUID,
        val stopId: UUID,
        val stopNumber: String?,
        val stopName: String?,
        val status: String,
        val consultationRequestedAt: OffsetDateTime,
        val desiredContractStartDate: LocalDate?,
        val createdAt: OffsetDateTime,
        val assigneeId: UUID?,
        val consultations: List<ConsultationHistory>?,
    ) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        data class ConsultationHistory(
            val consultationId: UUID,
            val status: String,
            val note: String?,
            val createdAt: OffsetDateTime,
        )
    }
}
