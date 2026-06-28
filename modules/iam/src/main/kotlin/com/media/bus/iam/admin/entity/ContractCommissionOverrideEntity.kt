package com.media.bus.iam.admin.entity

import com.media.bus.common.entity.common.DateBaseEntity
import com.media.bus.common.entity.common.UuidV7
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.java.UUIDEntityClass
import java.math.BigDecimal
import java.util.*

/**
 * ## 계약별 정산 비율 오버라이드 Exposed DAO 엔티티
 *
 * Master가 특정 계약에 한해 정산 비율을 임의로 설정할 때 생성된다.
 * 팩토리 메서드 `create()`로만 생성을 허용한다.
 */
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

    /** reservation.contract.id 논리적 참조 */
    var contractId by ContractCommissionOverrideTable.contractId
    /** auth.member.id 논리적 참조 (계약 담당 영업사원) */
    var memberId by ContractCommissionOverrideTable.memberId
    /** 오버라이드 정산 비율(%) */
    var commissionRate by ContractCommissionOverrideTable.commissionRate
    /** 수정한 Master 회원 */
    var updatedBy by ContractCommissionOverrideTable.updatedBy

    /** 오버라이드 비율을 변경한다 */
    fun updateRate(newRate: BigDecimal, changedBy: UUID) {
        commissionRate = newRate
        updatedBy = changedBy
    }
}
