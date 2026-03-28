package com.media.bus.reservation.contract.dto.request;

import com.media.bus.reservation.contract.entity.enums.PaymentCycle;
import com.media.bus.reservation.contract.entity.enums.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/// 계약 생성 요청 DTO.
@Schema(description = "계약 생성 요청")
public record CreateContractRequest(

    @NotNull
    @Schema(description = "정류소 UUID (stop 테이블 PK)", example = "018f1e2a-0000-7000-8000-000000000001")
    UUID stopId,

    @NotBlank
    @Size(max = 300)
    @Schema(description = "계약명", example = "광역버스 정류소 광고 계약")
    String contractName,

    @NotNull
    @Positive
    @Schema(description = "총 계약금액", example = "1200000.00")
    BigDecimal totalAmount,

    @Positive
    @Schema(description = "납부금액 (분할납 1회 금액)", example = "100000.00")
    BigDecimal payAmount,

    @NotNull
    @Schema(description = "납부 주기 (MONTHLY / QUARTERLY / ANNUALLY)", example = "MONTHLY")
    PaymentCycle paymentCycle,

    @NotNull
    @Schema(description = "납부 방법 (BANK_TRANSFER / CARD / CASH)", example = "BANK_TRANSFER")
    PaymentMethod paymentMethod,

    @NotNull
    @Schema(description = "계약 시작일", example = "2026-04-01T00:00:00+09:00")
    OffsetDateTime contractStartDate,

    @NotNull
    @Schema(description = "계약 종료일", example = "2027-03-31T23:59:59+09:00")
    OffsetDateTime contractEndDate
) {
}