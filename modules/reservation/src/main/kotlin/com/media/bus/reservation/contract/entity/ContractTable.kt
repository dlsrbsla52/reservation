package com.media.bus.reservation.contract.entity

import com.media.bus.common.entity.common.DateBaseTable
import com.media.bus.reservation.contract.entity.enums.ContractStatus
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

/**
 * ## 계약 테이블 정의
 *
 * Exposed DAO 기반 테이블 object. `reservation.contract` 테이블에 매핑된다.
 * 인덱스: stop_id+status 복합, previous_contract_id, member_id
 */
object ContractTable : DateBaseTable("reservation.contract") {
    val stopId = javaUUID("stop_id").index("idx_contract_stop_id")
    val previousContractId = javaUUID("previous_contract_id").nullable()
        .index("idx_contract_previous_contract_id")
    val memberId = javaUUID("member_id").index("idx_contract_member_id")
    val managerId = javaUUID("manager_id").nullable()
    val contractName = varchar("contract_name", 300)
    val status = enumerationByName<ContractStatus>("status", 20)
    val autoRenewal = bool("auto_renewal").default(false)
    val contractStartDate = timestampWithTimeZone("contract_start_date")
    val contractEndDate = timestampWithTimeZone("contract_end_date")
    val renewalNotifiedAt = timestampWithTimeZone("renewal_notified_at").nullable()
    val cancelledAt = timestampWithTimeZone("cancelled_at").nullable()
    val cancelReason = varchar("cancel_reason", 500).nullable()
}
