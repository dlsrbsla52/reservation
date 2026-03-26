package com.media.bus.stop.controller;

import com.media.bus.common.result.type.CommonResult;
import com.media.bus.common.web.response.DataView;
import com.media.bus.contract.entity.member.MemberCategory;
import com.media.bus.contract.entity.member.Permission;
import com.media.bus.contract.security.annotation.Authorize;
import com.media.bus.stop.dto.response.StopBulkRegisterResult;
import com.media.bus.stop.service.StopRegisterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "정류장 등록 API", description = "공공 API 기반 버스 정류소 일괄 등록")
@RestController
@RequestMapping("/api/v1/stop/register")
@RequiredArgsConstructor
public class StopRegisterController {

    private final StopRegisterService stopRegisterService;

    // TODO : 추후 배치 등으로 서비스에서 호출하게 되면 s2sTokenFilter 등을 통해 endpoint 접근 제어가 필요할 수 있다.
    @Operation(
        summary = "공공 API 전체 정류소 업데이트 및 일괄 등록",
        description = "서울 열린데이터광장 getBusStopInfo API에서 전체 버스 정류소를 가져와 DB에 저장합니다. ADMIN 권한 필요."
    )
    @Authorize(categories = {MemberCategory.ADMIN}, permissions = {Permission.MANAGE})
    @PostMapping("/bulk")
    public DataView<StopBulkRegisterResult> registerAllFromPublicApi() {
        return DataView.<StopBulkRegisterResult>builder()
                .result(CommonResult.SUCCESS)
                .data(stopRegisterService.registerAllFromPublicApi())
                .build();
    }
}
