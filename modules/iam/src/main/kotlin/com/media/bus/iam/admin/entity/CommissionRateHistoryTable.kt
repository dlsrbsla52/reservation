package com.media.bus.iam.admin.entity

import com.media.bus.common.entity.common.BaseTable
import com.media.bus.iam.admin.entity.enums.CommissionChangeType
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import java.time.OffsetDateTime
import java.time.ZoneId

/** 정산 비율 변경 이력 테이블 */
object CommissionRateHistoryTable : BaseTable("auth.commission_rate_history") {
    val memberId = javaUUID("member_id").index("idx_commission_rate_history_member_id")
    val contractId = javaUUID("contract_id").nullable().index("idx_commission_rate_history_contract_id")
    val changeType = enumerationByName<CommissionChangeType>("change_type", 20)
    val previousRate = decimal("previous_rate", 5, 2).nullable()
    val newRate = decimal("new_rate", 5, 2)
    val reason = varchar("reason", 500).nullable()
    val changedBy = javaUUID("changed_by")
    val createdAt = timestampWithTimeZone("created_at")
        .also { it.defaultValueFun = { OffsetDateTime.now(ZoneId.of("Asia/Seoul")) } }
}
