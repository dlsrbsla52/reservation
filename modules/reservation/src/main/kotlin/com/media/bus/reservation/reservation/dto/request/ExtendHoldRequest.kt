package com.media.bus.reservation.reservation.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import java.time.OffsetDateTime

@Schema(description = "선점 기간 연장 요청")
data class ExtendHoldRequest(
    @field:NotNull
    @param:Schema(description = "새 선점 만료 일시 (현재 시각 이후)", example = "2026-12-01T18:00:00+09:00")
    val holdExpiresAt: OffsetDateTime,
)
