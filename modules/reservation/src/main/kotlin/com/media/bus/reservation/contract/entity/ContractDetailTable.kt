package com.media.bus.reservation.contract.entity

import com.media.bus.common.entity.common.DateBaseTable
import com.media.bus.reservation.contract.entity.enums.PaymentCycle
import com.media.bus.reservation.contract.entity.enums.PaymentMethod
import com.media.bus.reservation.contract.entity.enums.PaymentStatus
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

/** 계약 상세 테이블 정의 */
object ContractDetailTable : DateBaseTable("reservation.contract_detail") {
    val contractId = javaUUID("contract_id").uniqueIndex("contract_detail_contract_id_key")
    val totalAmount = decimal("total_amount", 15, 2)
    val payAmount = decimal("pay_amount", 15, 2).nullable()
    val paymentCycle = enumerationByName<PaymentCycle>("payment_cycle", 20)
    val paymentMethod = enumerationByName<PaymentMethod>("payment_method", 30)
    val paymentStatus = enumerationByName<PaymentStatus>("payment_status", 20)
        .default(PaymentStatus.UNPAID)
    val paidAmount = decimal("paid_amount", 15, 2).nullable()
    val paidAt = timestampWithTimeZone("paid_at").nullable()
}
