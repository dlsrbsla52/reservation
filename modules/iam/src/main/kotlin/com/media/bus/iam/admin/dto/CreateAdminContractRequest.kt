package com.media.bus.iam.admin.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.*
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.*

/** 관리자 계약 생성 요청. reservation 도메인 타입과 직접 결합하지 않는다. */
@Schema(description = "관리자 계약 생성 요청")
data class CreateAdminContractRequest(
    @field:NotNull val stopId: UUID,
    @field:NotBlank @field:Size(max = 300) val contractName: String,
    @field:Positive @field:Digits(integer = 13, fraction = 2)
    @param:Schema(description = "직접 지정할 총 계약금액. 생략하면 정류소 월 단가로 산정")
    val totalAmount: BigDecimal?,
    @field:Positive val payAmount: BigDecimal?,
    @field:NotBlank @param:Schema(example = "MONTHLY") val paymentCycle: String,
    @field:NotBlank @param:Schema(example = "BANK_TRANSFER") val paymentMethod: String,
    val memberId: UUID?,
    val note: String?,
    @field:NotNull val contractStartDate: OffsetDateTime,
    @param:Schema(description = "종료 시각. 해당 시각은 계약 기간에 포함되지 않음")
    val contractEndDate: OffsetDateTime?,
    @field:Positive
    @param:Schema(description = "계약 개월 수. 종료 시각과 둘 중 하나만 지정")
    val durationMonths: Int? = null,
    @param:Schema(description = "영업 담당자 회원 ID. 마스터만 다른 담당자를 지정 가능")
    val managerId: UUID? = null,
)
