package com.media.bus.iam.admin.dto

import com.media.bus.iam.admin.entity.ManagerCommissionEntity
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.*

/** 영업사원 기본 정산 비율 응답 */
data class ManagerCommissionResponse(
    val memberId: UUID,
    val commissionRate: BigDecimal,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
) {
    companion object {
        fun of(entity: ManagerCommissionEntity) = ManagerCommissionResponse(
            memberId = entity.memberId,
            commissionRate = entity.commissionRate,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )
    }
}
