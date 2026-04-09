package com.media.bus.reservation.reservation.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

@Schema(description = "회원의 예약 요청 확인을 위한 DTO")
data class CreateStopReservationRequest(
    @Schema(description = "요청 stopId")
    val stopId: UUID,

    @Schema(description = "상담 요청 일자")
    val consultationRequestedAt: OffsetDateTime,

    @Schema(description = "계약 시작 희망 일자")
    val desiredContractStartDate: LocalDate?,
)
