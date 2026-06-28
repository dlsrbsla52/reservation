package com.media.bus.iam.admin.dto

import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.util.*

/** 계약별 정산 비율 오버라이드 등록/수정 요청 */
data class UpsertContractCommissionOverrideRequest(
    @field:NotNull(message = "담당 영업사원 ID는 필수입니다.")
    val memberId: UUID,
    @field:NotNull(message = "정산 비율은 필수입니다.")
    @field:DecimalMin(value = "0.00", message = "정산 비율은 0% 이상이어야 합니다.")
    @field:DecimalMax(value = "100.00", message = "정산 비율은 100% 이하이어야 합니다.")
    val rate: BigDecimal,
    val reason: String? = null,
)
