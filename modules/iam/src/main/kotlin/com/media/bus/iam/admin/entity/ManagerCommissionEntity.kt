package com.media.bus.iam.admin.entity

import com.media.bus.common.entity.common.DateBaseEntity
import com.media.bus.common.entity.common.UuidV7
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.java.UUIDEntityClass
import java.math.BigDecimal
import java.util.*

/**
 * ## 영업사원 기본 정산 비율 Exposed DAO 엔티티
 *
 * 팩토리 메서드 `create()`로만 생성을 허용한다.
 * 최초 생성 시 기본값 10%를 적용한다.
 */
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

    /** auth.member.id 논리적 참조 */
    var memberId by ManagerCommissionTable.memberId
    /** 정산 비율(%) */
    var commissionRate by ManagerCommissionTable.commissionRate

    /** 정산 비율을 변경한다 */
    fun updateRate(newRate: BigDecimal) {
        commissionRate = newRate
    }
}
