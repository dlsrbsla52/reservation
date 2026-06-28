package com.media.bus.iam.admin.dto

import com.media.bus.iam.admin.entity.ContractCommissionOverrideEntity
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.*

/** 계약별 정산 비율 오버라이드 응답 */
data class ContractCommissionOverrideResponse(
    val contractId: UUID,
    val memberId: UUID,
    val commissionRate: BigDecimal,
    val updatedBy: UUID,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
) {
    companion object {
        fun of(entity: ContractCommissionOverrideEntity) = ContractCommissionOverrideResponse(
            contractId = entity.contractId,
            memberId = entity.memberId,
            commissionRate = entity.commissionRate,
            updatedBy = entity.updatedBy,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )
    }
}
