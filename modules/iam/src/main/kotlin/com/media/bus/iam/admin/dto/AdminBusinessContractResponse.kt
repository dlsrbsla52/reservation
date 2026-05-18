package com.media.bus.iam.admin.dto

import com.media.bus.iam.client.reservation.dto.AdminContractView
import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.*

/**
 * ## 어드민 사이트 전용 계약 결합 응답 DTO
 *
 * 설계 의도:
 * - reservation 내부 API의 응답(`AdminContractView`)을 어드민 API의 공개 응답으로 변환한다.
 * - client 패키지의 DTO는 외부 노출하지 않고, 어드민 API 계약 변경 시 영향 범위를 이 클래스로 한정한다.
 * - 도메인 Enum은 모듈 경계 원칙에 따라 String으로 직렬화한다.
 */
@Schema(description = "관리자 전용 계약 결합 응답")
data class AdminBusinessContractResponse(
    @param:Schema(description = "계약 ID")
    val id: UUID,

    @param:Schema(description = "정류소 PK (UUID)")
    val stopId: UUID,

    @param:Schema(description = "정류소 번호(STOPS_NO) — stop 서비스 장애 시 null", nullable = true)
    val stopNumber: String?,

    @param:Schema(description = "정류소 이름 — stop 서비스 장애 또는 삭제된 정류소일 경우 null", nullable = true)
    val stopName: String?,

    @param:Schema(description = "매니저 회원 ID")
    val memberId: UUID,

    @param:Schema(description = "계약명")
    val contractName: String,

    @param:Schema(description = "계약 상태(PENDING/ACTIVE/EXPIRED/CANCELLED)")
    val status: String,

    @param:Schema(description = "계약 시작일")
    val contractStartDate: OffsetDateTime,

    @param:Schema(description = "계약 종료일")
    val contractEndDate: OffsetDateTime,

    @param:Schema(description = "총 계약금액", nullable = true)
    val totalAmount: BigDecimal?,

    @param:Schema(description = "분할납 1회 금액", nullable = true)
    val payAmount: BigDecimal?,

    @param:Schema(description = "납부 주기(MONTHLY/QUARTERLY/ANNUALLY)", nullable = true)
    val paymentCycle: String?,

    @param:Schema(description = "납부 방법(BANK_TRANSFER/CARD/CASH)", nullable = true)
    val paymentMethod: String?,

    @param:Schema(description = "납부 상태(UNPAID/PAID/OVERDUE)", nullable = true)
    val paymentStatus: String?,

    @param:Schema(description = "납부 완료 금액", nullable = true)
    val paidAmount: BigDecimal?,

    @param:Schema(description = "납부 완료 일시", nullable = true)
    val paidAt: OffsetDateTime?,

    @param:Schema(description = "계약 생성일시")
    val createdAt: OffsetDateTime,
) {
    companion object {
        /** reservation 내부 응답 DTO를 어드민 API 응답으로 변환한다. */
        fun from(view: AdminContractView): AdminBusinessContractResponse = AdminBusinessContractResponse(
            id = view.id,
            stopId = view.stopId,
            stopNumber = view.stopNumber,
            stopName = view.stopName,
            memberId = view.memberId,
            contractName = view.contractName,
            status = view.status,
            contractStartDate = view.contractStartDate,
            contractEndDate = view.contractEndDate,
            totalAmount = view.totalAmount,
            payAmount = view.payAmount,
            paymentCycle = view.paymentCycle,
            paymentMethod = view.paymentMethod,
            paymentStatus = view.paymentStatus,
            paidAmount = view.paidAmount,
            paidAt = view.paidAt,
            createdAt = view.createdAt,
        )
    }
}
