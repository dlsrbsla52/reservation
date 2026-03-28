package com.media.bus.reservation.controller;

import com.media.bus.common.result.type.CommonResult;
import com.media.bus.common.web.response.DataView;
import com.media.bus.common.web.response.NoDataView;
import com.media.bus.contract.security.MemberPrincipal;
import com.media.bus.contract.security.annotation.Authorize;
import com.media.bus.contract.security.annotation.CurrentMember;
import com.media.bus.reservation.dto.request.CreateStopReservationRequest;
import com.media.bus.reservation.service.ReservationFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "예약 API", description = "회원이 신청하는 예약, 예약 조회, 예약 취소 등과 관련된 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/reservation")
public class ReservationController {

    private final ReservationFacade facade;

    /// 예약 생성.
    @Authorize
    @Operation(summary = "예약 생성", description = "새로운 예약을 생성합니다.")
    @PostMapping
    public NoDataView createReservation(
        @CurrentMember MemberPrincipal principal,
        @RequestBody @Valid CreateStopReservationRequest request
    ) {

        facade.createReservation(principal, request);

        return NoDataView.builder()
                .result(CommonResult.SUCCESS)
                .build();
    }

    /// 내 예약 목록 조회.
    @Operation(summary = "내 예약 목록 조회", description = "로그인한 사용자의 예약 목록을 조회합니다.")
    @org.springframework.web.bind.annotation.GetMapping("/me")
    public DataView<List<Object>> getMyReservations(
        @CurrentMember MemberPrincipal principal
    ) {
        // TODO: 예약 목록 조회 로직 구현
        return DataView.<List<Object>>builder()
                .result(CommonResult.SUCCESS)
                .data(List.of())
                .build();
    }


}
