package com.media.bus.stop.controller;

import com.media.bus.common.result.type.CommonResult;
import com.media.bus.common.web.response.DataView;
import com.media.bus.common.web.response.NoDataView;
import com.media.bus.stop.dto.request.SimpleStopCreateRequest;
import com.media.bus.stop.dto.response.BusStopResponse;
import com.media.bus.stop.service.StopService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "정류장 API", description = "버스 정류장의 정보 등과 관련된 API")
@RestController
@RequestMapping("/api/v1/stop")
@RequiredArgsConstructor
public class StopController {
    
    private final StopService stopService;

    @Operation(summary = "정류소 단건 조회", description = "버스 정류소 조회")
    @GetMapping("/{stopId}")
    public DataView<BusStopResponse> getBusStop(@PathVariable String stopId) {
        return DataView.<BusStopResponse>builder()
            .result(CommonResult.SUCCESS)
            .data(stopService.getBusStop(stopId))
            .build();
    }
    
    @Operation(summary = "정류소 단건 등록", description = "버스 정류소의 단건 수기 등록")
    @PostMapping("/simple")
    public NoDataView saveSimpleBusStop(
        @RequestHeader("Authorization") String token,
        @RequestBody @Valid SimpleStopCreateRequest request
    ) {

        stopService.createOneStop(token, request);

        return NoDataView.builder()
                .result(CommonResult.SUCCESS)
                .build();
    }

}
