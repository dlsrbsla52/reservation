package com.media.bus.iam.admin.entity

import com.media.bus.common.entity.common.DateBaseTable
import org.jetbrains.exposed.v1.core.java.javaUUID
import java.math.BigDecimal

/** 영업사원 기본 정산 비율 테이블 */
object ManagerCommissionTable : DateBaseTable("auth.manager_commission") {
    val memberId = javaUUID("member_id").uniqueIndex("uq_manager_commission_member_id")
    val commissionRate = decimal("commission_rate", 5, 2).default(BigDecimal("10.00"))
}
