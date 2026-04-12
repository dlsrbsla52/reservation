package com.media.bus.reservation.reservation.dto.response

import com.media.bus.reservation.reservation.entity.ReservationEntity
import com.media.bus.reservation.reservation.entity.enums.ReservationStatus
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

/**
 * ## 내 예약 목록 조회 응답 DTO
 *
 * 목록에 표시할 최소 정보만 노출한다. 상세 정보(상담 이력 등)는 단건 조회 API 를 사용한다.
 */
@Schema(description = "내 예약 목록 row")
data class MyReservationResponse(
    @param:Schema(description = "예약 ID")
    val reservationId: UUID,

    @param:Schema(description = "정류소 PK (UUID)")
    val stopId: UUID,

    @param:Schema(description = "정류소 번호 (STOPS_NO) — stop 서비스 장애 시 null", nullable = true)
    val stopNumber: String?,

    @param:Schema(description = "정류소 이름 — stop 서비스 장애 또는 삭제된 정류소일 경우 null", nullable = true)
    val stopName: String?,

    @param:Schema(description = "현재 상태")
    val status: ReservationStatus,

    @param:Schema(description = "상담 요청 일시")
    val consultationRequestedAt: OffsetDateTime,

    @param:Schema(description = "계약 시작 희망 일자")
    val desiredContractStartDate: LocalDate?,

    @param:Schema(description = "예약 생성 일시")
    val createdAt: OffsetDateTime,
) {
    companion object {
        /** Facade에서 bulk 조회로 얻은 stop 정보를 주입한다. 누락 시 null로 fallback. */
        fun of(
            reservation: ReservationEntity,
            status: ReservationStatus,
            stopNumber: String? = null,
            stopName: String? = null,
        ): MyReservationResponse =
            MyReservationResponse(
                reservationId = reservation.id.value,
                stopId = reservation.stopId,
                stopNumber = stopNumber,
                stopName = stopName,
                status = status,
                consultationRequestedAt = reservation.consultationRequestedAt,
                desiredContractStartDate = reservation.desiredContractStartDate,
                createdAt = reservation.createdAt,
            )
    }
}
