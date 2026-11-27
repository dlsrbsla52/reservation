package com.media.bus.reservation.contract.dto.response

import com.media.bus.reservation.contract.entity.ContractDetailEntity
import com.media.bus.reservation.contract.entity.ContractEntity
import com.media.bus.reservation.contract.entity.enums.ContractStatus
import com.media.bus.reservation.contract.entity.enums.PaymentCycle
import com.media.bus.reservation.contract.entity.enums.PaymentMethod
import com.media.bus.reservation.contract.entity.enums.PaymentStatus
import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.*

/** 관리자 전용 계약 결합 응답 DTO */
@Schema(description = "관리자 전용 계약 결합 응답")
data class AdminContractView(
    @param:Schema(description = "계약 ID")
    val id: UUID,

    @param:Schema(description = "정류소 PK (UUID)")
    val stopId: UUID,

    @param:Schema(description = "정류소 번호(STOPS_NO) — stop 서비스 장애 시 null", nullable = true)
    val stopNumber: String?,

    @param:Schema(description = "정류소 이름 — stop 서비스 장애 또는 삭제된 정류소일 경우 null", nullable = true)
    val stopName: String?,

    @param:Schema(description = "매니저 회원 ID")
    val memberId: UUID?,

    @param:Schema(description = "계약명")
    val contractName: String,

    @param:Schema(description = "계약 상태")
    val status: ContractStatus,

    @param:Schema(description = "계약 시작일")
    val contractStartDate: OffsetDateTime,

    @param:Schema(description = "계약 종료일")
    val contractEndDate: OffsetDateTime,

    @param:Schema(description = "총 계약금액 — 상세 누락 시 null", nullable = true)
    val totalAmount: BigDecimal?,

    @param:Schema(description = "분할납 1회 금액", nullable = true)
    val payAmount: BigDecimal?,

    @param:Schema(description = "납부 주기 — 상세 누락 시 null", nullable = true)
    val paymentCycle: PaymentCycle?,

    @param:Schema(description = "납부 방법 — 상세 누락 시 null", nullable = true)
    val paymentMethod: PaymentMethod?,

    @param:Schema(description = "납부 상태 — 상세 누락 시 null", nullable = true)
    val paymentStatus: PaymentStatus?,

    @param:Schema(description = "납부 완료 금액", nullable = true)
    val paidAmount: BigDecimal?,

    @param:Schema(description = "납부 완료 일시", nullable = true)
    val paidAt: OffsetDateTime?,

    @param:Schema(description = "계약 생성일시")
    val createdAt: OffsetDateTime,
    val managerId: UUID? = null,
) {
    companion object {
        /** Contract + ContractDetail 엔티티로부터 admin view DTO를 생성한다. */
        fun from(contract: ContractEntity, detail: ContractDetailEntity?): AdminContractView =
            AdminContractView(
                id = contract.id.value,
                stopId = contract.stopId,
                stopNumber = null,
                stopName = null,
                memberId = contract.memberId,
                managerId = contract.managerId,
                contractName = contract.contractName,
                status = contract.status,
                contractStartDate = contract.contractStartDate,
                contractEndDate = contract.contractEndDate,
                totalAmount = detail?.totalAmount,
                payAmount = detail?.payAmount,
                paymentCycle = detail?.paymentCycle,
                paymentMethod = detail?.paymentMethod,
                paymentStatus = detail?.paymentStatus,
                paidAmount = detail?.paidAmount,
                paidAt = detail?.paidAt,
                createdAt = contract.createdAt,
            )
    }
}
