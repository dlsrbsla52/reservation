package com.media.bus.reservation.reservation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "회원의 예약 요청 확인을 위한 record")
public record CreateStopReservationRequest(

    @NotBlank(message = "정류장의 ID는 빈값일 수 없습니다.")
    @Schema(description = "요청 stopId")
    UUID stopId,

    @NotBlank(message = "상담 요청 일자는 빈값일 수 없습니다.")
    @Schema(description = "상담 요청 일자")
    OffsetDateTime consultationRequestedAt,

    @Schema(description = "계약 시작 희망 일자")
    LocalDate desiredContractStartDate

) {
}
