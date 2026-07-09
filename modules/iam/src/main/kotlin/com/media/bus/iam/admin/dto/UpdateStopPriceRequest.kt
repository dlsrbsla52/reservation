package com.media.bus.iam.admin.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import java.math.BigDecimal

@Schema(description = "정류소 단가 수정 요청")
data class UpdateStopPriceRequest(

    @param:Schema(description = "변경할 정류소 단가 (원)", example = "1500000.00")
    @field:NotNull(message = "단가를 입력해주세요.")
    @field:Positive(message = "단가는 0보다 커야 합니다.")
    val unitPrice: BigDecimal,
)
