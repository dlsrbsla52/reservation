package com.media.bus.reservation.contract.entity

import com.media.bus.common.entity.common.DateBaseEntity
import com.media.bus.common.entity.common.UuidV7
import com.media.bus.reservation.contract.dto.request.CreateContractRequest
import com.media.bus.reservation.contract.entity.enums.ContractStatus
import com.media.bus.reservation.reservation.dto.response.StopInfo
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.java.UUIDEntityClass
import java.time.OffsetDateTime
import java.util.*

/** 계약 Exposed DAO 엔티티 */
class ContractEntity(id: EntityID<UUID>) : DateBaseEntity(id, ContractTable) {
    companion object : UUIDEntityClass<ContractEntity>(ContractTable) {

        /** 정류소 정보, 요청 DTO로부터 Contract 엔티티를 생성하는 팩토리 메서드. */
        fun create(stopInfo: StopInfo, request: CreateContractRequest, slotNo: Short): ContractEntity =
            new(UuidV7.generate()) {
                stopId = stopInfo.id
                this.slotNo = slotNo
                memberId = request.memberId
                contractName = request.contractName
                status = ContractStatus.PENDING
                autoRenewal = false
                contractStartDate = request.contractStartDate
                contractEndDate = request.contractEndDate
            }
    }

    var stopId by ContractTable.stopId

    var slotNo by ContractTable.slotNo

    var previousContractId by ContractTable.previousContractId

    var memberId by ContractTable.memberId

    var managerId by ContractTable.managerId

    var contractName by ContractTable.contractName

    var status by ContractTable.status

    var autoRenewal by ContractTable.autoRenewal

    var contractStartDate by ContractTable.contractStartDate

    var contractEndDate by ContractTable.contractEndDate

    var renewalNotifiedAt by ContractTable.renewalNotifiedAt

    var cancelledAt by ContractTable.cancelledAt

    var cancelReason by ContractTable.cancelReason

    /** 계약을 취소 상태로 변경하는 도메인 행위 메서드. */
    fun cancel(cancelledAt: OffsetDateTime, reason: String? = null) {
        this.status = ContractStatus.CANCELLED
        this.cancelledAt = cancelledAt
        this.cancelReason = reason
    }
}
