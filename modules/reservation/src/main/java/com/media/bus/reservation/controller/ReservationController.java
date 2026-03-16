package com.media.bus.reservation.controller;

import com.media.bus.common.result.type.CommonResult;
import com.media.bus.common.web.response.DataView;
import com.media.bus.common.web.response.NoDataView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "예약 API", description = "예약, 예약 조회, 예약 취소 등과 관련된 API")
@RestController
@RequestMapping("/api/v1/reservation")
public class ReservationController {

    /**
     * 예약 생성.
     * Gateway에서 검증된 JWT를 기반으로 X-User-Id 헤더를 주입받아 사용합니다.
     */
    @Operation(summary = "예약 생성", description = "새로운 예약을 생성합니다.")
    @PostMapping
    public NoDataView createReservation(
        @RequestHeader("X-User-Id") String userId,
        @RequestBody @Valid Object request
    ) {
        // TODO: 예약 생성 로직 구현
        return NoDataView.builder()
                .result(CommonResult.SUCCESS)
                .build();
    }

    /**
     * 내 예약 목록 조회.
     */
    @Operation(summary = "내 예약 목록 조회", description = "로그인한 사용자의 예약 목록을 조회합니다.")
    @org.springframework.web.bind.annotation.GetMapping("/me")
    public DataView<List<Object>> getMyReservations(
        @RequestHeader("X-User-Id") String userId
    ) {
        // TODO: 예약 목록 조회 로직 구현
        return DataView.<List<Object>>builder()
                .result(CommonResult.SUCCESS)
                .data(List.of())
                .build();
    }


}
