package com.media.bus.iam.admin.dto

import com.media.bus.iam.admin.entity.CommissionRateHistoryEntity
import com.media.bus.iam.admin.entity.enums.CommissionChangeType
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.*

/** 정산 비율 변경 이력 응답 */
data class CommissionRateHistoryResponse(
    val id: UUID,
    val memberId: UUID,
    val contractId: UUID?,
    val changeType: CommissionChangeType,
    val previousRate: BigDecimal?,
    val newRate: BigDecimal,
    val reason: String?,
    val changedBy: UUID,
    val createdAt: OffsetDateTime,
) {
    companion object {
        fun of(entity: CommissionRateHistoryEntity) = CommissionRateHistoryResponse(
            id = entity.id.value,
            memberId = entity.memberId,
            contractId = entity.contractId,
            changeType = entity.changeType,
            previousRate = entity.previousRate,
            newRate = entity.newRate,
            reason = entity.reason,
            changedBy = entity.changedBy,
            createdAt = entity.createdAt,
        )
    }
}
