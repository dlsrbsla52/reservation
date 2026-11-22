package com.media.bus.reservation.contract.entity

import com.media.bus.common.entity.common.DateBaseEntity
import com.media.bus.common.entity.common.UuidV7
import com.media.bus.reservation.contract.dto.request.CreateContractRequest
import com.media.bus.reservation.contract.entity.enums.PaymentStatus
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.java.UUIDEntityClass
import java.util.*

/** 계약 상세 Exposed DAO 엔티티 */
class ContractDetailEntity(id: EntityID<UUID>) : DateBaseEntity(id, ContractDetailTable) {
    companion object : UUIDEntityClass<ContractDetailEntity>(ContractDetailTable) {

        /** Contract 엔티티와 요청 DTO로부터 ContractDetail을 생성하는 팩토리 메서드. */
        fun create(contract: ContractEntity, request: CreateContractRequest): ContractDetailEntity =
            new(UuidV7.generate()) {
                contractId = contract.id.value
                totalAmount = request.totalAmount
                payAmount = request.payAmount
                paymentCycle = request.paymentCycle
                paymentMethod = request.paymentMethod
                paymentStatus = PaymentStatus.UNPAID
            }
    }

    var contractId by ContractDetailTable.contractId

    var totalAmount by ContractDetailTable.totalAmount

    var payAmount by ContractDetailTable.payAmount

    var paymentCycle by ContractDetailTable.paymentCycle

    var paymentMethod by ContractDetailTable.paymentMethod

    var paymentStatus by ContractDetailTable.paymentStatus

    var paidAmount by ContractDetailTable.paidAmount

    var paidAt by ContractDetailTable.paidAt
}
