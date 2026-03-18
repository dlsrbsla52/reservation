package com.media.bus.stop.controller;

import com.media.bus.common.result.type.CommonResult;
import com.media.bus.common.web.response.NoDataView;
import com.media.bus.stop.dto.request.SimpleStopCreateRequest;
import com.media.bus.stop.service.StopService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "정류장 API", description = "버스 정류장의 정보 등과 관련된 API")
@RestController
@RequestMapping("/api/v1/stop")
@RequiredArgsConstructor
public class StopController {
    
    private final StopService stopService;
    
    @Operation(summary = "회원가입", description = "새로운 회원을 가입시킵니다. 성공 시 이메일 인증 토큰을 반환합니다.")
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
