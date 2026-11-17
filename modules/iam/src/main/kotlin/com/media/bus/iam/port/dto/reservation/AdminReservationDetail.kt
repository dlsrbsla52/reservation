package com.media.bus.iam.port.dto.reservation

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

/**
 * ## 관리자 예약 상세 (`ReservationAdminPort` 응답이자 관리자 API 응답)
 *
 * reservation 모듈의 `ReservationDetailResponse`를 iam 쪽 타입으로 옮긴 것이다. 상태 enum은 모듈 경계상 이름 문자열로 받는다.
 */
data class AdminReservationDetail(
    val reservationId: UUID,
    val memberId: UUID,
    val stopId: UUID,
    val stopNumber: String?,
    val stopName: String?,
    val status: String,
    /** 선점 만료 일시. 진행 중(PENDING/CONSULTING)일 때만 값이 있다. */
    val holdExpiresAt: OffsetDateTime?,
    val consultationRequestedAt: OffsetDateTime,
    val desiredContractStartDate: LocalDate,
    val createdAt: OffsetDateTime,
    val assigneeId: UUID?,
    val consultations: List<ConsultationHistory>?,
) {
    // 공개 예약 상세의 ConsultationHistory(status 가 enum)와 통합 OpenAPI 스키마 이름이 겹치지 않게 한다
    @Schema(name = "AdminConsultationHistory")
    data class ConsultationHistory(
        val consultationId: UUID,
        val status: String,
        val note: String?,
        val createdAt: OffsetDateTime,
    )
}
