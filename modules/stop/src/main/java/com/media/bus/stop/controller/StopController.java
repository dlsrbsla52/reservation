package com.media.bus.stop.controller;

import com.media.bus.common.result.type.CommonResult;
import com.media.bus.common.web.response.DataView;
import com.media.bus.common.web.response.NoDataView;
import com.media.bus.common.web.wrapper.PageResult;
import com.media.bus.contract.entity.member.MemberCategory;
import com.media.bus.contract.entity.member.Permission;
import com.media.bus.contract.security.MemberPrincipal;
import com.media.bus.contract.security.annotation.Authorize;
import com.media.bus.contract.security.annotation.CurrentMember;
import com.media.bus.stop.dto.request.SimpleStopCreateRequest;
import com.media.bus.stop.dto.response.BusStopResponse;
import com.media.bus.stop.service.StopService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "정류장 API", description = "버스 정류장의 정보 등과 관련된 API")
@RestController
@RequestMapping("/api/v1/stop")
@RequiredArgsConstructor
public class StopController {

    private final StopService stopService;

    @Operation(summary = "정류소 단건 조회", description = "버스 정류소 조회")
    @Authorize(categories = {MemberCategory.ADMIN})
    @GetMapping("/{stopId}")
    public DataView<BusStopResponse> getBusStop(@PathVariable String stopId) {
        return DataView.<BusStopResponse>builder()
            .result(CommonResult.SUCCESS)
            .data(stopService.getBusStop(stopId))
            .build();
    }

    @Operation(summary = "정류소 리스트 조회", description = "정류소 이름 전방 일치 검색 (idx_stop_name 인덱스 활용, 'text%' 패턴만 허용)")
    @Authorize(categories = {MemberCategory.USER})
    @GetMapping("/name/{stopName}")
    public PageResult<BusStopResponse> getBusStopByName(
        @PathVariable String stopName,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        return stopService.getStopsByName(stopName, pageable);
    }

    @Operation(summary = "정류소 단건 등록", description = "버스 정류소의 단건 수기 등록 (ADMIN 권한 필요)")
    @Authorize(categories = {MemberCategory.ADMIN}, permissions = {Permission.WRITE})
    @PostMapping("/simple")
    public NoDataView saveSimpleBusStop(
        @CurrentMember MemberPrincipal principal,
        @RequestBody @Valid SimpleStopCreateRequest request
    ) {
        stopService.createOneStop(principal, request);

        return NoDataView.builder()
                .result(CommonResult.SUCCESS)
                .build();
    }
}
