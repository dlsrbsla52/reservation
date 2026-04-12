package com.media.bus.reservation.reservation.dto.response

import com.media.bus.reservation.reservation.entity.ReservationConsultationEntity
import com.media.bus.reservation.reservation.entity.ReservationEntity
import com.media.bus.reservation.reservation.entity.enums.ReservationStatus
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

/**
 * ## 예약 단건 상세 응답 DTO
 *
 * 예약의 기본 정보 + 상담 이력 전체 + 현재 상태를 함께 제공한다.
 */
@Schema(description = "예약 상세")
data class ReservationDetailResponse(
    @Schema(description = "예약 ID")
    val reservationId: UUID,

    @Schema(description = "회원 ID")
    val memberId: UUID,

    @Schema(description = "정류소 PK (UUID)")
    val stopId: UUID,

    @Schema(description = "정류소 번호 (STOPS_NO) — stop 서비스 장애 시 null", nullable = true)
    val stopNumber: String?,

    @Schema(description = "정류소 이름 — stop 서비스 장애 또는 삭제된 정류소일 경우 null", nullable = true)
    val stopName: String?,

    @Schema(description = "현재 상태")
    val status: ReservationStatus,

    @Schema(description = "상담 요청 일시")
    val consultationRequestedAt: OffsetDateTime,

    @Schema(description = "계약 시작 희망 일자")
    val desiredContractStartDate: LocalDate?,

    @Schema(description = "예약 생성 일시")
    val createdAt: OffsetDateTime,

    @Schema(description = "상담 이력 (생성일 오름차순)")
    val consultations: List<ConsultationHistory>,
) {

    @Schema(description = "상담 이력 항목")
    data class ConsultationHistory(
        val consultationId: UUID,
        val status: ReservationStatus,
        val note: String?,
        val createdAt: OffsetDateTime,
    ) {
        companion object {
            fun of(entity: ReservationConsultationEntity): ConsultationHistory = ConsultationHistory(
                consultationId = entity.id.value,
                status = entity.status,
                note = entity.note,
                createdAt = entity.createdAt,
            )
        }
    }

    companion object {
        /** Facade가 stop 서비스 bulk 조회로 얻은 정보를 주입한다. 없으면 null로 fallback. */
        fun of(
            reservation: ReservationEntity,
            currentStatus: ReservationStatus,
            consultations: List<ReservationConsultationEntity>,
            stopNumber: String? = null,
            stopName: String? = null,
        ): ReservationDetailResponse = ReservationDetailResponse(
            reservationId = reservation.id.value,
            memberId = reservation.memberId,
            stopId = reservation.stopId,
            stopNumber = stopNumber,
            stopName = stopName,
            status = currentStatus,
            consultationRequestedAt = reservation.consultationRequestedAt,
            desiredContractStartDate = reservation.desiredContractStartDate,
            createdAt = reservation.createdAt,
            consultations = consultations.map(ConsultationHistory::of),
        )
    }
}
