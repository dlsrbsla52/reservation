package com.media.bus.reservation.contract.dto.request

import com.media.bus.reservation.contract.entity.enums.PaymentCycle
import com.media.bus.reservation.contract.entity.enums.PaymentMethod
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.*

/** ## 계약 생성 요청 DTO */
@Schema(description = "계약 생성 요청")
data class CreateContractRequest(
    @field:NotNull
    @param:Schema(description = "정류소 UUID (stop 테이블 PK)", example = "018f1e2a-0000-7000-8000-000000000001")
    val stopId: UUID,

    @field:NotBlank
    @field:Size(max = 300)
    @param:Schema(description = "계약명", example = "광역버스 정류소 광고 계약")
    val contractName: String,

    @field:NotNull
    @field:Positive
    @param:Schema(description = "총 계약금액", example = "1200000.00")
    val totalAmount: BigDecimal,

    @field:Positive
    @param:Schema(description = "납부금액 (분할납 1회 금액)", example = "100000.00")
    val payAmount: BigDecimal?,

    @field:NotNull
    @param:Schema(description = "납부 주기 (MONTHLY / QUARTERLY / ANNUALLY)", example = "MONTHLY")
    val paymentCycle: PaymentCycle,

    @field:NotNull
    @param:Schema(description = "납부 방법 (BANK_TRANSFER / CARD / CASH)", example = "BANK_TRANSFER")
    val paymentMethod: PaymentMethod,

    @param:Schema(description = "계약 회원 아이디", example = "018f1e2a-0000-7000-8000-000000000001")
    val memberId: UUID?,

    @param:Schema(description = "계약에 관련된 메모", example = "이 사람은 회원 아이디가 없어요 그냥 이름으로 메모할게여")
    val note: String?,

    @field:NotNull
    @param:Schema(description = "계약 시작일", example = "2026-04-01T00:00:00+09:00")
    val contractStartDate: OffsetDateTime,

    @field:NotNull
    @param:Schema(description = "계약 종료일", example = "2027-03-31T23:59:59+09:00")
    val contractEndDate: OffsetDateTime
)
