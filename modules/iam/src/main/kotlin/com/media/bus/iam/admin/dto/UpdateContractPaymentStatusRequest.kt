package com.media.bus.iam.admin.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import java.math.BigDecimal
import java.time.OffsetDateTime

data class UpdateContractPaymentStatusRequest(
    @field:NotBlank
    @param:Schema(allowableValues = ["UNPAID", "PAID", "OVERDUE"], example = "PAID")
    val paymentStatus: String,
    @field:Positive
    @field:Digits(integer = 13, fraction = 2)
    @param:Schema(description = "납부완료 시 실납부 금액. 생략하면 기존 납부 금액 또는 총 계약금액 적용")
    val paidAmount: BigDecimal?,
    @param:Schema(description = "납부완료 시 납부 일시. 생략하면 기존 납부 일시 또는 현재 시각 적용")
    val paidAt: OffsetDateTime?,
)
