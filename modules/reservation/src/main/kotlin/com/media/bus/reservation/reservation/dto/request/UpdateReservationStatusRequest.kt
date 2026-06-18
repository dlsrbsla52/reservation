package com.media.bus.reservation.reservation.dto.request

import com.media.bus.reservation.reservation.entity.enums.ReservationStatus
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

@Schema(description = "예약 상태 변경 요청")
data class UpdateReservationStatusRequest(
    @field:NotNull
    @param:Schema(description = "변경할 예약 상태 (CONSULTING, COMPLETED, CANCELLED, REJECTED)", example = "CONSULTING")
    val status: ReservationStatus,

    @field:Size(max = 1000)
    @param:Schema(description = "상담 메모 / 변경 사유", example = "고객님과 1차 유선 전화 상담 완료 및 대면 일정 수립")
    val note: String?,
)
