package com.media.bus.reservation.contract.entity

import com.media.bus.common.entity.common.DateBaseEntity
import com.media.bus.common.entity.common.UuidV7
import com.media.bus.reservation.contract.dto.request.CreateContractRequest
import com.media.bus.reservation.contract.dto.response.MemberInfo
import com.media.bus.reservation.contract.entity.enums.ContractStatus
import com.media.bus.reservation.reservation.dto.response.StopInfo
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.java.UUIDEntityClass
import java.time.OffsetDateTime
import java.util.*

/**
 * ## 계약 Exposed DAO 엔티티
 *
 * `ContractTable`에 매핑되는 DAO 엔티티 클래스.
 * 계약 초기 상태는 항상 PENDING, autoRenewal은 false로 설정한다.
 */
class ContractEntity(id: EntityID<UUID>) : DateBaseEntity(id, ContractTable) {
    companion object : UUIDEntityClass<ContractEntity>(ContractTable) {

        /**
         * IAM 회원 정보, 정류소 정보, 요청 DTO로부터 Contract 엔티티를 생성하는 팩토리 메서드.
         * 계약 초기 상태는 항상 PENDING, autoRenewal은 false로 설정한다.
         *
         * @param memberInfo IAM DB에서 재검증된 회원 정보
         * @param stopInfo   stop 서비스에서 확인된 정류소 정보
         * @param request    계약 생성 요청 DTO
         * @return 저장된 ContractEntity 인스턴스
         */
        fun create(memberInfo: MemberInfo, stopInfo: StopInfo, request: CreateContractRequest): ContractEntity =
            new(UuidV7.generate()) {
                stopId = stopInfo.id
                memberId = memberInfo.id
                contractName = request.contractName
                status = ContractStatus.PENDING
                autoRenewal = false
                contractStartDate = request.contractStartDate
                contractEndDate = request.contractEndDate
            }
    }

    var stopId by ContractTable.stopId
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

    /**
     * 계약을 취소 상태로 변경하는 도메인 행위 메서드.
     * Setter를 통한 외부 직접 변경 대신 명시적 의도를 드러낸다.
     *
     * @param cancelledAt 취소 처리 일시
     * @param reason 취소 사유 (선택)
     */
    fun cancel(cancelledAt: OffsetDateTime, reason: String? = null) {
        this.status = ContractStatus.CANCELLED
        this.cancelledAt = cancelledAt
        this.cancelReason = reason
    }
}
