package com.media.bus.iam.admin.dto

import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal

/** 영업사원 기본 정산 비율 수정 요청 */
data class UpdateManagerCommissionRateRequest(
    @field:NotNull(message = "정산 비율은 필수입니다.")
    @field:DecimalMin(value = "0.00", message = "정산 비율은 0% 이상이어야 합니다.")
    @field:DecimalMax(value = "100.00", message = "정산 비율은 100% 이하이어야 합니다.")
    val rate: BigDecimal,
    val reason: String? = null,
)
