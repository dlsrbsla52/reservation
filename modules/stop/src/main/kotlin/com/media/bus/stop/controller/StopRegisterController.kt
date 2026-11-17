package com.media.bus.stop.controller

import com.media.bus.common.web.response.ApiResponse
import com.media.bus.contract.entity.member.MemberCategory
import com.media.bus.contract.entity.member.Permission
import com.media.bus.contract.security.annotation.Authorize
import com.media.bus.stop.dto.response.StopBulkRegisterResult
import com.media.bus.stop.service.StopRegisterService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "정류장 등록 API", description = "공공 API 기반 버스 정류소 일괄 등록")
@RestController
@RequestMapping("/api/v1/stop/register")
class StopRegisterController(
    private val stopRegisterService: StopRegisterService,
) {

    // 배치 등 앱 내부에서 실행하게 되면 이 엔드포인트가 아니라 StopRegisterService 를 직접 호출한다(같은 프로세스라 HTTP 가 필요 없다).
    @Operation(
        summary = "공공 API 전체 정류소 업데이트 및 일괄 등록",
        description = "서울 열린데이터광장 getBusStopInfo API에서 전체 버스 정류소를 가져와 DB에 저장합니다. ADMIN 권한 필요.",
    )
    @Authorize(categories = [MemberCategory.ADMIN], permissions = [Permission.MANAGE])
    @PostMapping("/bulk")
    fun registerAllFromPublicApi(): ApiResponse<StopBulkRegisterResult> =
        ApiResponse.success(stopRegisterService.registerAllFromPublicApi())
}
