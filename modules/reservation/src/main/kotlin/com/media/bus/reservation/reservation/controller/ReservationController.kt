package com.media.bus.reservation.reservation.controller

import com.media.bus.common.web.response.ApiResponse
import com.media.bus.contract.security.MemberPrincipal
import com.media.bus.contract.security.annotation.Authorize
import com.media.bus.contract.security.annotation.CurrentMember
import com.media.bus.reservation.reservation.dto.request.CreateStopReservationRequest
import com.media.bus.reservation.reservation.service.ReservationFacade
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*

@Tag(name = "예약 API", description = "회원이 신청하는 예약, 예약 조회, 예약 취소 등과 관련된 API")
@RestController
@RequestMapping("/api/v1/reservation")
class ReservationController(
    private val facade: ReservationFacade,
) {
    /// 예약 생성.
    @Authorize
    @Operation(summary = "예약 생성", description = "새로운 예약을 생성합니다.")
    @PostMapping
    fun createReservation(
        @CurrentMember principal: MemberPrincipal,
        @RequestBody @Valid request: CreateStopReservationRequest,
    ): ApiResponse<Unit?> {
        facade.createReservation(principal, request)
        return ApiResponse.success()
    }

    /// 내 예약 목록 조회.
    @Operation(summary = "내 예약 목록 조회", description = "로그인한 사용자의 예약 목록을 조회합니다.")
    @GetMapping("/me")
    fun getMyReservations(
        @CurrentMember principal: MemberPrincipal,
    ): ApiResponse<List<Any>> {
        // TODO: 예약 목록 조회 로직 구현
        return ApiResponse.success(emptyList())
    }
}
