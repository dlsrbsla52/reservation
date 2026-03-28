package com.media.bus.reservation.contract.dto.response;

import com.media.bus.reservation.contract.entity.Contract;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

/// 계약 생성 응답 DTO.
@Schema(description = "계약 응답")
public record ContractResponse(

    @Schema(description = "계약 ID")
    UUID id,

    @Schema(description = "정류소 ID")
    UUID stopId,

    @Schema(description = "회원 ID")
    UUID memberId,

    @Schema(description = "계약명")
    String contractName,

    @Schema(description = "계약 상태")
    String status,

    @Schema(description = "계약 시작일")
    OffsetDateTime contractStartDate,

    @Schema(description = "계약 종료일")
    OffsetDateTime contractEndDate,

    @Schema(description = "생성일시")
    OffsetDateTime createdAt
) {

    /// Contract 엔티티로부터 응답 DTO를 생성하는 정적 팩토리 메서드.
    public static ContractResponse from(Contract contract) {
        return new ContractResponse(
            contract.getId(),
            contract.getStopId(),
            contract.getMemberId(),
            contract.getContractName(),
            contract.getStatus(),
            contract.getContractStartDate(),
            contract.getContractEndDate(),
            contract.getCreatedAt()
        );
    }
}