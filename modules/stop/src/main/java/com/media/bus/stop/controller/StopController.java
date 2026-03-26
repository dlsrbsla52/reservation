package com.media.bus.stop.controller;

import com.media.bus.common.result.type.CommonResult;
import com.media.bus.common.web.response.ListData;
import com.media.bus.common.web.response.NoDataView;
import com.media.bus.common.web.response.PageView;
import com.media.bus.contract.entity.member.MemberCategory;
import com.media.bus.contract.entity.member.Permission;
import com.media.bus.contract.security.MemberPrincipal;
import com.media.bus.contract.security.annotation.Authorize;
import com.media.bus.contract.security.annotation.CurrentMember;
import com.media.bus.stop.dto.request.SimpleStopCreateRequest;
import com.media.bus.stop.dto.request.StopSearchCriteria;
import com.media.bus.stop.dto.response.BusStopResponse;
import com.media.bus.stop.service.StopService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "정류장 API", description = "버스 정류장의 정보 등과 관련된 API")
@RestController
@RequestMapping("/api/v1/stop")
@RequiredArgsConstructor
public class StopController {

    private final StopService stopService;

    @Operation(
        summary = "정류소 조회",
        description = "pk(UUID), stopId(정류소 번호), stopName(이름 완전 일치) 중 하나를 쿼리 파라미터로 전달. 우선순위: pk > stopId > stopName. stopName은 동명 정류소 시 다건 반환"
    )
    @Authorize(categories = {MemberCategory.ADMIN})
    @GetMapping
    public PageView<BusStopResponse> getBusStop(
        @RequestParam(required = false) UUID pk,
        @RequestParam(required = false) String stopId,
        @RequestParam(required = false) String stopName
    ) {
        return PageView.<BusStopResponse>builder()
            .result(CommonResult.SUCCESS)
            .data(ListData.<BusStopResponse>builder()
                .list(stopService.getBusStop(StopSearchCriteria.of(pk, stopId, stopName)))
                .build())
            .build();
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