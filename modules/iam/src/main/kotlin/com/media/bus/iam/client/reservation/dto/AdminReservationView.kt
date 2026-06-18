package com.media.bus.iam.client.reservation.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

@JsonIgnoreProperties(ignoreUnknown = true)
data class AdminReservationView(
    val reservationId: UUID,
    val memberId: UUID,
    val stopId: UUID,
    val stopNumber: String?,
    val stopName: String?,
    val status: String,
    val assigneeId: UUID?,
    val consultationRequestedAt: OffsetDateTime,
    val desiredContractStartDate: LocalDate?,
    val createdAt: OffsetDateTime,
)
