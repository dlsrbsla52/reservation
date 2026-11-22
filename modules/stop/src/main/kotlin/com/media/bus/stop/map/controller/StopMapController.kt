package com.media.bus.stop.map.controller

import com.media.bus.common.web.response.ApiResponse
import com.media.bus.contract.entity.member.MemberCategory
import com.media.bus.contract.security.annotation.Authorize
import com.media.bus.stop.map.dto.StopBoundsCriteria
import com.media.bus.stop.map.dto.StopBoundsResponse
import com.media.bus.stop.map.service.StopMapQueryService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** 지도 연동 전용 정류소 컨트롤러 */
@Tag(name = "정류장 지도 API", description = "지도 화면 전용 정류소 좌표 조회")
@RestController
@RequestMapping("/api/v1/stop/map")
class StopMapController(
    private val stopMapQueryService: StopMapQueryService,
) {

    /** 지도 영역(남서·북동 좌표) 안의 정류소를 조회한다. */
    @Operation(
        summary = "지도 영역 내 정류소 조회",
        description = "카카오맵 `map.getBounds()` 의 남서/북동 좌표로 영역 내 정류소 마커를 조회한다. " +
            "기본 상한 1,000건(최대 2,000건)이며, 상한을 넘으면 truncated=true 로 알린다. " +
            "stopsType 을 주면 해당 유형만 조회한다.",
    )
    @Authorize(categories = [MemberCategory.USER, MemberCategory.BUSINESS, MemberCategory.ADMIN])
    @GetMapping("/bounds")
    fun getStopsInBounds(
        @RequestParam swLat: String,
        @RequestParam swLng: String,
        @RequestParam neLat: String,
        @RequestParam neLng: String,
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) stopsType: String?,
    ): ApiResponse<StopBoundsResponse> =
        ApiResponse.success(
            stopMapQueryService.findMarkersInBounds(
                StopBoundsCriteria.of(swLat, swLng, neLat, neLng, limit, stopsType),
            ),
        )
}
