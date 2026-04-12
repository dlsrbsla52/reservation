package com.media.bus.reservation.reservation.controller

import com.media.bus.common.web.response.ApiResponse
import com.media.bus.common.web.wrapper.PageResult
import com.media.bus.contract.security.MemberPrincipal
import com.media.bus.contract.security.annotation.Authorize
import com.media.bus.contract.security.annotation.CurrentMember
import com.media.bus.reservation.reservation.dto.request.CreateStopReservationRequest
import com.media.bus.reservation.reservation.dto.response.MyReservationResponse
import com.media.bus.reservation.reservation.dto.response.ReservationDetailResponse
import com.media.bus.reservation.reservation.service.ReservationFacade
import com.media.bus.reservation.reservation.service.ReservationService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*
import java.util.*

@Tag(name = "예약 API", description = "회원이 신청하는 예약, 예약 조회, 예약 취소 등과 관련된 API")
@RestController
@RequestMapping("/api/v1/reservation")
class ReservationController(
    private val facade: ReservationFacade,
    private val reservationService: ReservationService,
) {
    /// 예약 생성.
    @Authorize
    @Operation(summary = "예약 생성", description = "새로운 예약을 생성합니다.")
    @PostMapping
    fun createReservation(
        @CurrentMember principal: MemberPrincipal,
        @RequestBody @Valid request: CreateStopReservationRequest,
    ): ApiResponse<UUID> {
        val reservationId = facade.createReservation(principal, request)
        return ApiResponse.success(reservationId)
    }

    /// 내 예약 목록 조회 (페이지네이션).
    @Authorize
    @Operation(summary = "내 예약 목록 조회", description = "로그인한 사용자의 예약 목록을 페이지 단위로 조회합니다.")
    @GetMapping("/me")
    fun getMyReservations(
        @CurrentMember principal: MemberPrincipal,
        @Parameter(description = "0-base 페이지 번호") @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "20") size: Int,
    ): ApiResponse<PageResult<MyReservationResponse>> =
        ApiResponse.success(facade.getMyReservations(principal, page, size))

    /// 예약 단건 상세 조회.
    @Authorize
    @Operation(summary = "예약 단건 상세 조회", description = "예약 ID로 상세 정보와 상담 이력을 조회합니다. 본인 예약만 조회 가능합니다.")
    @GetMapping("/{reservationId}")
    fun getReservationDetail(
        @CurrentMember principal: MemberPrincipal,
        @PathVariable reservationId: UUID,
    ): ApiResponse<ReservationDetailResponse> =
        ApiResponse.success(facade.getReservationDetail(principal, reservationId))

    /// 예약 취소.
    @Authorize
    @Operation(summary = "예약 취소", description = "상담 전(PENDING) 단계에서만 예약을 취소할 수 있습니다.")
    @DeleteMapping("/{reservationId}")
    fun cancelReservation(
        @CurrentMember principal: MemberPrincipal,
        @PathVariable reservationId: UUID,
    ): ApiResponse<Unit?> {
        reservationService.cancelReservation(principal.id, reservationId)
        return ApiResponse.success()
    }
}
