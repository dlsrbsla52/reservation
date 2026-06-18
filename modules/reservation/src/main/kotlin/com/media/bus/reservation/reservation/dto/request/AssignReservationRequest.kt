package com.media.bus.reservation.reservation.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import java.util.*

@Schema(description = "예약 담당자 배정 요청")
data class AssignReservationRequest(
    @field:NotNull
    @param:Schema(description = "담당 어드민 회원 ID", example = "018f1e2a-0000-7000-8000-000000000001")
    val assigneeId: UUID,
)
