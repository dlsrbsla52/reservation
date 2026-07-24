package com.media.bus.iam.admin.dto

import com.media.bus.iam.client.reservation.dto.ContractView
import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime
import java.util.*

/** 관리자 계약 생성 응답. */
@Schema(description = "관리자 계약 응답")
data class AdminContractResponse(
    val id: UUID,
    val stopId: UUID,
    val stopNumber: String?,
    val stopName: String?,
    val memberId: UUID?,
    val contractName: String,
    val status: String,
    val contractStartDate: OffsetDateTime,
    val contractEndDate: OffsetDateTime,
    val createdAt: OffsetDateTime,
) {
    companion object {
        fun from(view: ContractView) = AdminContractResponse(
            id = view.id,
            stopId = view.stopId,
            stopNumber = view.stopNumber,
            stopName = view.stopName,
            memberId = view.memberId,
            contractName = view.contractName,
            status = view.status,
            contractStartDate = view.contractStartDate,
            contractEndDate = view.contractEndDate,
            createdAt = view.createdAt,
        )
    }
}
