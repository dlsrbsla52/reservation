package com.media.bus.iam.admin.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.*

/** 관리자 계약 생성 요청. reservation 도메인 타입과 직접 결합하지 않는다. */
@Schema(description = "관리자 계약 생성 요청")
data class CreateAdminContractRequest(
    @field:NotNull val stopId: UUID,
    @field:NotBlank @field:Size(max = 300) val contractName: String,
    @field:NotNull @field:Positive val totalAmount: BigDecimal,
    @field:Positive val payAmount: BigDecimal?,
    @field:NotBlank @param:Schema(example = "MONTHLY") val paymentCycle: String,
    @field:NotBlank @param:Schema(example = "BANK_TRANSFER") val paymentMethod: String,
    val memberId: UUID?,
    val note: String?,
    @field:NotNull val contractStartDate: OffsetDateTime,
    @field:NotNull val contractEndDate: OffsetDateTime,
)
