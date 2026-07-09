package com.media.bus.stop.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import java.math.BigDecimal
import java.util.*

@Schema(description = "정류소 단가 수정 요청 (내부 전용)")
data class UpdateStopPriceRequest(

    @param:Schema(description = "변경할 정류소 단가 (원)", example = "1500000.00")
    @field:NotNull(message = "단가를 입력해주세요.")
    @field:Positive(message = "단가는 0보다 커야 합니다.")
    val unitPrice: BigDecimal,

    @param:Schema(description = "변경한 관리자 회원 ID", example = "018f1e2a-0000-7000-8000-000000000002")
    val changedById: UUID?,
)
