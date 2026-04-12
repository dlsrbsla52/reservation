package com.media.bus.reservation.contract.dto.response

import com.media.bus.reservation.contract.entity.ContractEntity
import com.media.bus.reservation.contract.entity.enums.ContractStatus
import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime
import java.util.*

/** ## 계약 생성 응답 DTO */
@Schema(description = "계약 응답")
data class ContractResponse(
    @Schema(description = "계약 ID") val id: UUID,
    @Schema(description = "정류소 PK (UUID)") val stopId: UUID,
    @Schema(description = "정류소 번호 (STOPS_NO) — stop 서비스 장애 시 null", nullable = true)
    val stopNumber: String?,
    @Schema(description = "정류소 이름 — stop 서비스 장애 또는 삭제된 정류소일 경우 null", nullable = true)
    val stopName: String?,
    @Schema(description = "회원 ID") val memberId: UUID,
    @Schema(description = "계약명") val contractName: String,
    @Schema(description = "계약 상태") val status: ContractStatus,
    @Schema(description = "계약 시작일") val contractStartDate: OffsetDateTime,
    @Schema(description = "계약 종료일") val contractEndDate: OffsetDateTime,
    @Schema(description = "생성일시") val createdAt: OffsetDateTime,
) {
    companion object {
        /**
         * ContractEntity로부터 응답 DTO를 생성한다.
         *
         * @param stopNumber stop 서비스에서 조회한 정류소 번호 (없으면 null)
         * @param stopName   stop 서비스에서 조회한 정류소 이름 (없으면 null)
         */
        fun from(
            contract: ContractEntity,
            stopNumber: String? = null,
            stopName: String? = null,
        ): ContractResponse = ContractResponse(
            id = contract.id.value,
            stopId = contract.stopId,
            stopNumber = stopNumber,
            stopName = stopName,
            memberId = contract.memberId,
            contractName = contract.contractName,
            status = contract.status,
            contractStartDate = contract.contractStartDate,
            contractEndDate = contract.contractEndDate,
            createdAt = contract.createdAt,
        )
    }
}
