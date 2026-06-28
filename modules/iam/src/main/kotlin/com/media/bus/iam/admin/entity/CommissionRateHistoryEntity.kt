package com.media.bus.iam.admin.entity

import com.media.bus.common.entity.common.BaseEntity
import com.media.bus.common.entity.common.UuidV7
import com.media.bus.iam.admin.entity.enums.CommissionChangeType
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.java.UUIDEntityClass
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.*

/**
 * ## 정산 비율 변경 이력 Exposed DAO 엔티티
 *
 * append-only. 생성 후 수정 불가.
 * 팩토리 메서드 `record()`로만 생성을 허용한다.
 */
class CommissionRateHistoryEntity(id: EntityID<UUID>) : BaseEntity(id) {

    companion object : UUIDEntityClass<CommissionRateHistoryEntity>(CommissionRateHistoryTable) {

        /** 정산 비율 변경 이력을 기록한다 */
        fun record(
            memberId: UUID,
            changeType: CommissionChangeType,
            previousRate: BigDecimal?,
            newRate: BigDecimal,
            changedBy: UUID,
            contractId: UUID? = null,
            reason: String? = null,
        ): CommissionRateHistoryEntity = new(UuidV7.generate()) {
            this.memberId = memberId
            this.contractId = contractId
            this.changeType = changeType
            this.previousRate = previousRate
            this.newRate = newRate
            this.reason = reason
            this.changedBy = changedBy
            this.createdAt = OffsetDateTime.now(ZoneId.of("Asia/Seoul"))
        }
    }

    var memberId by CommissionRateHistoryTable.memberId
    var contractId by CommissionRateHistoryTable.contractId
    var changeType by CommissionRateHistoryTable.changeType
    var previousRate by CommissionRateHistoryTable.previousRate
    var newRate by CommissionRateHistoryTable.newRate
    var reason by CommissionRateHistoryTable.reason
    var changedBy by CommissionRateHistoryTable.changedBy
    var createdAt by CommissionRateHistoryTable.createdAt
}
