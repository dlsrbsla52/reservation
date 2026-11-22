package com.media.bus.iam.admin.entity

import com.media.bus.common.entity.common.DateBaseEntity
import com.media.bus.common.entity.common.UuidV7
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.java.UUIDEntityClass
import java.math.BigDecimal
import java.util.*

/** 영업사원 기본 정산 비율 Exposed DAO 엔티티 */
class ManagerCommissionEntity(id: EntityID<UUID>) : DateBaseEntity(id, ManagerCommissionTable) {

    companion object : UUIDEntityClass<ManagerCommissionEntity>(ManagerCommissionTable) {

        /** 영업사원 기본 정산 비율 레코드 생성 */
        fun create(
            memberId: UUID,
            commissionRate: BigDecimal = BigDecimal("10.00"),
        ): ManagerCommissionEntity = new(UuidV7.generate()) {
            this.memberId = memberId
            this.commissionRate = commissionRate
        }
    }

    var memberId by ManagerCommissionTable.memberId
    var commissionRate by ManagerCommissionTable.commissionRate

    /** 정산 비율을 변경한다 */
    fun updateRate(newRate: BigDecimal) {
        commissionRate = newRate
    }
}
