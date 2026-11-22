package com.media.bus.iam.admin.entity

import com.media.bus.common.entity.common.DateBaseEntity
import com.media.bus.common.entity.common.UuidV7
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.java.UUIDEntityClass
import java.math.BigDecimal
import java.util.*

/** 계약별 정산 비율 오버라이드 Exposed DAO 엔티티 */
class ContractCommissionOverrideEntity(id: EntityID<UUID>) : DateBaseEntity(id, ContractCommissionOverrideTable) {

    companion object : UUIDEntityClass<ContractCommissionOverrideEntity>(ContractCommissionOverrideTable) {

        /** 계약별 정산 비율 오버라이드 레코드 생성 */
        fun create(
            contractId: UUID,
            memberId: UUID,
            commissionRate: BigDecimal,
            updatedBy: UUID,
        ): ContractCommissionOverrideEntity = new(UuidV7.generate()) {
            this.contractId = contractId
            this.memberId = memberId
            this.commissionRate = commissionRate
            this.updatedBy = updatedBy
        }
    }

    var contractId by ContractCommissionOverrideTable.contractId
    var memberId by ContractCommissionOverrideTable.memberId
    var commissionRate by ContractCommissionOverrideTable.commissionRate
    var updatedBy by ContractCommissionOverrideTable.updatedBy

    /** 오버라이드 비율을 변경한다 */
    fun updateRate(newRate: BigDecimal, changedBy: UUID) {
        commissionRate = newRate
        updatedBy = changedBy
    }
}
