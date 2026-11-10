package com.media.bus.reservation.contract.entity

import com.media.bus.common.entity.common.DateBaseTable
import com.media.bus.reservation.contract.entity.enums.ContractStatus
import com.media.bus.reservation.slot.StopSlot
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

/**
 * ## 계약 테이블 정의
 *
 * Exposed DAO 기반 테이블 object. `reservation.contract` 테이블에 매핑된다.
 * 인덱스: stop_id+status 복합, previous_contract_id, member_id
 *
 * 기간 독점 제약은 DB 에 있다: 제외 제약 `ex_contract_slot_period` 가 같은 광고면(stop_id + slot_no)에서
 * 점유 계약(PENDING/ACTIVE)의 기간 겹침을 막는다. Exposed 로 표현하지 않고 Liquibase changelog(V003)에서만 관리한다.
 */
object ContractTable : DateBaseTable("reservation.contract") {
    val stopId = javaUUID("stop_id").index("idx_contract_stop_id")
    val slotNo = short("slot_no").default(StopSlot.DEFAULT_NO)
    val previousContractId = javaUUID("previous_contract_id").nullable()
        .index("idx_contract_previous_contract_id")
    val memberId = javaUUID("member_id").nullable().index("idx_contract_member_id")
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
