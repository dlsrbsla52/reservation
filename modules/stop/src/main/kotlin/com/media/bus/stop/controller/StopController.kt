package com.media.bus.stop.controller

import com.media.bus.common.web.response.ApiResponse
import com.media.bus.common.web.response.ListData
import com.media.bus.contract.entity.member.MemberCategory
import com.media.bus.contract.entity.member.Permission
import com.media.bus.contract.security.MemberPrincipal
import com.media.bus.contract.security.annotation.Authorize
import com.media.bus.contract.security.annotation.CurrentMember
import com.media.bus.stop.dto.request.SimpleStopCreateRequest
import com.media.bus.stop.dto.request.StopSearchCriteria
import com.media.bus.stop.dto.response.BusStopResponse
import com.media.bus.stop.service.StopService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*
import java.util.*

@Tag(name = "정류장 API", description = "버스 정류장의 정보 등과 관련된 API")
@RestController
@RequestMapping("/api/v1/stop")
class StopController(
    private val stopService: StopService,
) {

    @Operation(
        summary = "정류소 조회",
        description = "pk(UUID), stopId(정류소 번호), stopName(이름 완전 일치) 중 하나를 쿼리 파라미터로 전달. 우선순위: pk > stopId > stopName. stopName은 동명 정류소 시 다건 반환",
    )
    @Authorize(categories = [MemberCategory.ADMIN])
    @GetMapping
    fun getBusStop(
        @RequestParam(required = false) pk: UUID?,
        @RequestParam(required = false) stopId: String?,
        @RequestParam(required = false) stopName: String?,
    ): ApiResponse<ListData<BusStopResponse>> =
        ApiResponse.page(stopService.getBusStop(StopSearchCriteria.of(pk, stopId, stopName)))

    @Operation(summary = "정류소 단건 등록", description = "버스 정류소의 단건 수기 등록 (ADMIN 권한 필요)")
    @Authorize(categories = [MemberCategory.ADMIN], permissions = [Permission.WRITE])
    @PostMapping("/simple")
    fun saveSimpleBusStop(
        @CurrentMember principal: MemberPrincipal,
        @RequestBody @Valid request: SimpleStopCreateRequest,
    ): ApiResponse<Unit?> {
        stopService.createOneStop(principal, request)
        return ApiResponse.success()
    }
}
