package com.media.bus.reservation.reservation.dto.response

import com.media.bus.reservation.reservation.entity.ReservationEntity
import com.media.bus.reservation.reservation.entity.enums.ReservationStatus
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

@Schema(description = "어드민 예약 목록 조회 응답")
data class AdminReservationListResponse(
    @param:Schema(description = "예약 ID")
    val reservationId: UUID,

    @param:Schema(description = "예약 요청 회원 ID")
    val memberId: UUID,

    @param:Schema(description = "정류소 PK (UUID)")
    val stopId: UUID,

    @param:Schema(description = "정류소 번호 — stop 서비스 장애 시 null", nullable = true)
    val stopNumber: String?,

    @param:Schema(description = "정류소 이름 — stop 서비스 장애 시 null", nullable = true)
    val stopName: String?,

    @param:Schema(description = "현재 상태")
    val status: ReservationStatus,

    @param:Schema(description = "담당 어드민 ID (미지정 시 null)", nullable = true)
    val assigneeId: UUID?,

    @param:Schema(description = "상담 요청 일시")
    val consultationRequestedAt: OffsetDateTime,

    @param:Schema(description = "계약 시작 희망 일자")
    val desiredContractStartDate: LocalDate?,

    @param:Schema(description = "예약 생성 일시")
    val createdAt: OffsetDateTime,
) {
    companion object {
        fun of(
            reservation: ReservationEntity,
            status: ReservationStatus,
            stopNumber: String? = null,
            stopName: String? = null,
        ): AdminReservationListResponse =
            AdminReservationListResponse(
                reservationId = reservation.id.value,
                memberId = reservation.memberId,
                stopId = reservation.stopId,
                stopNumber = stopNumber,
                stopName = stopName,
                status = status,
                assigneeId = reservation.assigneeId,
                consultationRequestedAt = reservation.consultationRequestedAt,
                desiredContractStartDate = reservation.desiredContractStartDate,
                createdAt = reservation.createdAt,
            )
    }
}
