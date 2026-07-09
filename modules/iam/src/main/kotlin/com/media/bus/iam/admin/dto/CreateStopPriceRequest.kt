package com.media.bus.iam.admin.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import java.math.BigDecimal
import java.util.*

@Schema(description = "정류소 단가 등록 요청")
data class CreateStopPriceRequest(

    @param:Schema(description = "정류소 pk(UUID)", example = "018f1e2a-0000-7000-8000-000000000001")
    @field:NotNull(message = "정류소를 선택해주세요.")
    val stopId: UUID,

    @param:Schema(description = "정류소 단가 (원)", example = "1200000.00")
    @field:NotNull(message = "단가를 입력해주세요.")
    @field:Positive(message = "단가는 0보다 커야 합니다.")
    val unitPrice: BigDecimal,
)
