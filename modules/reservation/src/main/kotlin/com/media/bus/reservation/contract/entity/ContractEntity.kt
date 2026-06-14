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

/**
 * ## 계약 Exposed DAO 엔티티
 *
 * `ContractTable`에 매핑되는 DAO 엔티티 클래스.
 * 계약 초기 상태는 항상 PENDING, autoRenewal은 false로 설정한다.
 */
class ContractEntity(id: EntityID<UUID>) : DateBaseEntity(id, ContractTable) {
    companion object : UUIDEntityClass<ContractEntity>(ContractTable) {

        /**
         * 정류소 정보, 요청 DTO로부터 Contract 엔티티를 생성하는 팩토리 메서드.
         * 계약 초기 상태는 항상 PENDING, autoRenewal은 false로 설정한다.
         * memberId는 비회원 계약 시 null이 될 수 있다.
         *
         * @param stopInfo stop 서비스에서 확인된 정류소 정보
         * @param request  계약 생성 요청 DTO
         * @return 저장된 ContractEntity 인스턴스
         */
        fun create(stopInfo: StopInfo, request: CreateContractRequest): ContractEntity =
            new(UuidV7.generate()) {
                stopId = stopInfo.id
                memberId = request.memberId
                contractName = request.contractName
                status = ContractStatus.PENDING
                autoRenewal = false
                contractStartDate = request.contractStartDate
                contractEndDate = request.contractEndDate
            }
    }

    /** 계약이 연결된 정류소 UUID. stop 서비스에서 검증된 값이며, 복합 인덱스(stop_id + status) 대상. */
    var stopId by ContractTable.stopId

    /** 갱신 계약인 경우 이전 계약의 UUID. 신규 계약이면 null. */
    var previousContractId by ContractTable.previousContractId

    /** 계약 회원의 UUID. IAM DB에서 재검증된 값이다. */
    var memberId by ContractTable.memberId

    /** 담당 관리자의 UUID. 미배정 시 null. */
    var managerId by ContractTable.managerId

    /** 계약명 (최대 300자). */
    var contractName by ContractTable.contractName

    /** 계약 상태. 초기값은 PENDING이며, 이후 ACTIVE / EXPIRED / CANCELLED로 전이된다. */
    var status by ContractTable.status

    /** 계약 자동 갱신 여부. 초기값은 false이며, 갱신 동의 시 true로 변경된다. */
    var autoRenewal by ContractTable.autoRenewal

    /** 계약 시작일시 (timezone 포함). */
    var contractStartDate by ContractTable.contractStartDate

    /** 계약 종료일시 (timezone 포함). */
    var contractEndDate by ContractTable.contractEndDate

    /** 갱신 알림 발송 시각. 알림 미발송 시 null이며, 발송 완료 후 기록된다. */
    var renewalNotifiedAt by ContractTable.renewalNotifiedAt

    /** 계약 취소 처리 시각. 취소 전에는 null이다. */
    var cancelledAt by ContractTable.cancelledAt

    /** 취소 사유 (최대 500자). 취소 전에는 null이다. */
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
