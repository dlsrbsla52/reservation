package com.media.bus.reservation.reservation.controller

import com.media.bus.common.web.response.ApiResponse
import com.media.bus.common.web.wrapper.PageResult
import com.media.bus.reservation.reservation.dto.request.AssignReservationRequest
import com.media.bus.reservation.reservation.dto.request.CompleteToContractRequest
import com.media.bus.reservation.reservation.dto.request.ExtendHoldRequest
import com.media.bus.reservation.reservation.dto.request.UpdateReservationStatusRequest
import com.media.bus.reservation.reservation.dto.response.AdminReservationListResponse
import com.media.bus.reservation.reservation.dto.response.ReservationDetailResponse
import com.media.bus.reservation.reservation.entity.enums.ReservationStatus
import com.media.bus.reservation.reservation.service.ReservationFacade
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.*
import java.time.OffsetDateTime
import java.util.*

@Tag(name = "예약 조회 API S2S 전용", description = "MSA 내부 서비스 간 통신 전용 — 외부 호출 금지, S2S 토큰 필수")
@RestController
@RequestMapping("/api/v1/internal/reservation")
class InternalReservationController(
    private val facade: ReservationFacade,
) {
    companion object {
        private const val MAX_PAGE_SIZE = 100
    }

    @Operation(
        summary = "전체 예약 조건 검색 (내부 전용)",
        description = "상태, 담당자 ID, 정류소 ID, 상담 신청 기간 등을 필터링하여 전체 예약을 페이징 검색합니다."
    )
    @GetMapping("/reservations")
    fun searchAdminReservations(
        @RequestParam(required = false) status: ReservationStatus?,
        @RequestParam(required = false) assigneeId: UUID?,
        @RequestParam(required = false) stopId: UUID?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) createdFrom: OffsetDateTime?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) createdTo: OffsetDateTime?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ApiResponse<PageResult<AdminReservationListResponse>> {
        val safePage = page.coerceAtLeast(0)
        val safeSize = size.coerceIn(1, MAX_PAGE_SIZE)
        return ApiResponse.success(
            facade.searchAdminReservations(status, assigneeId, stopId, createdFrom, createdTo, safePage, safeSize)
        )
    }

    @Operation(
        summary = "예약 상세 조회 (내부 전용)",
        description = "특정 예약의 상세 정보와 상담 로그 전체를 함께 조회합니다."
    )
    @GetMapping("/reservations/{reservationId}")
    fun getReservationDetailForAdmin(
        @PathVariable reservationId: UUID
    ): ApiResponse<ReservationDetailResponse> =
        ApiResponse.success(facade.getReservationDetailForAdmin(reservationId))

    @Operation(
        summary = "예약 담당자 배정 (내부 전용)",
        description = "특정 예약에 담당 어드민을 배정 또는 변경합니다."
    )
    @PutMapping("/reservations/{reservationId}/assignee")
    fun assignReservation(
        @PathVariable reservationId: UUID,
        @RequestBody @Valid request: AssignReservationRequest
    ): ApiResponse<Unit?> {
        facade.assignReservation(reservationId, request.assigneeId)
        return ApiResponse.successWithMessage("예약 담당자가 배정되었습니다.")
    }

    @Operation(
        summary = "예약 상태 변경 (내부 전용)",
        description = "예약의 상태를 변경하고 상담 로그(메모)를 누적 기록합니다."
    )
    @PutMapping("/reservations/{reservationId}/status")
    fun updateReservationStatus(
        @PathVariable reservationId: UUID,
        @RequestBody @Valid request: UpdateReservationStatusRequest
    ): ApiResponse<Unit?> {
        facade.updateReservationStatus(reservationId, request.status, request.note)
        return ApiResponse.successWithMessage("예약 상태 및 상담 기록이 반영되었습니다.")
    }

    @Operation(
        summary = "선점 기간 연장 (내부 전용)",
        description = "진행 중(PENDING/CONSULTING)인 예약의 선점 만료 일시를 변경합니다."
    )
    @PutMapping("/reservations/{reservationId}/hold")
    fun extendHold(
        @PathVariable reservationId: UUID,
        @RequestBody @Valid request: ExtendHoldRequest
    ): ApiResponse<Unit?> {
        facade.extendHold(reservationId, request.holdExpiresAt)
        return ApiResponse.successWithMessage("선점 기간이 변경되었습니다.")
    }

    @Operation(
        summary = "예약 완료 및 계약 생성 (내부 전용)",
        description = "예약을 상담 완료(COMPLETED) 처리하고 연계된 광고 계약(Contract)을 신규 생성합니다."
    )
    @PostMapping("/reservations/{reservationId}/complete-to-contract")
    fun completeReservationToContract(
        @PathVariable reservationId: UUID,
        @RequestBody @Valid request: CompleteToContractRequest
    ): ApiResponse<Unit?> {
        facade.completeReservationToContract(reservationId, request)
        return ApiResponse.successWithMessage("예약이 완료되었으며, 신규 계약서가 발행되었습니다.")
    }
}