package com.media.bus.iam.admin.entity

import com.media.bus.common.entity.common.DateBaseTable
import org.jetbrains.exposed.v1.core.java.javaUUID

/** 계약별 정산 비율 오버라이드 테이블 */
object ContractCommissionOverrideTable : DateBaseTable("auth.contract_commission_override") {
    val contractId = javaUUID("contract_id").uniqueIndex("uq_contract_commission_override_contract_id")
    val memberId = javaUUID("member_id").index("idx_contract_commission_override_member_id")
    val commissionRate = decimal("commission_rate", 5, 2)
    val updatedBy = javaUUID("updated_by")
}
