package com.media.bus.reservation.contract.entity

import com.media.bus.common.entity.common.DateBaseEntity
import com.media.bus.common.entity.common.UuidV7
import com.media.bus.reservation.contract.dto.request.CreateContractRequest
import com.media.bus.reservation.contract.entity.enums.PaymentStatus
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.java.UUIDEntityClass
import java.util.*

/**
 * ## 계약 상세 Exposed DAO 엔티티
 *
 * `ContractDetailTable`에 매핑되는 DAO 엔티티 클래스.
 * 초기 납부 상태는 항상 UNPAID이다.
 */
class ContractDetailEntity(id: EntityID<UUID>) : DateBaseEntity(id, ContractDetailTable) {
    companion object : UUIDEntityClass<ContractDetailEntity>(ContractDetailTable) {

        /**
         * Contract 엔티티와 요청 DTO로부터 ContractDetail을 생성하는 팩토리 메서드.
         * 초기 납부 상태는 항상 UNPAID이다.
         *
         * @param contract 이미 저장된 ContractEntity
         * @param request  계약 생성 요청 DTO
         * @return 저장된 ContractDetailEntity 인스턴스
         */
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

    /** 연결된 계약의 UUID. `contract_detail`과 `contract`는 1:1 관계를 가진다. */
    var contractId by ContractDetailTable.contractId

    /** 계약 총액. 전체 계약 기간에 걸쳐 납부해야 할 총 금액 (소수점 2자리). */
    var totalAmount by ContractDetailTable.totalAmount

    /** 회차당 납부 금액. 납부 주기(paymentCycle)에 따른 1회 납부 금액이며, 미확정 시 null. */
    var payAmount by ContractDetailTable.payAmount

    /** 납부 주기. MONTHLY(월납) / QUARTERLY(분기납) / ANNUALLY(연납). */
    var paymentCycle by ContractDetailTable.paymentCycle

    /** 납부 방법. BANK_TRANSFER(계좌이체) / CARD(카드) / CASH(현금). */
    var paymentMethod by ContractDetailTable.paymentMethod

    /** 현재 납부 상태. 초기값은 항상 UNPAID이며, 납부 처리 시 PAID·OVERDUE로 전이된다. */
    var paymentStatus by ContractDetailTable.paymentStatus

    /** 실제 납부된 금액. 납부 완료 전에는 null이며, 부분 납부 시 누적 금액을 반영한다. */
    var paidAmount by ContractDetailTable.paidAmount

    /** 납부 완료 시각(timezone 포함). 납부 전에는 null이다. */
    var paidAt by ContractDetailTable.paidAt
}
