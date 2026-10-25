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

/**
 * ## 지도 연동 전용 정류소 컨트롤러
 *
 * 경로를 `/api/v1/stop/map` 하위에 둔 이유:
 * 게이트웨이가 `/api/v1/stop` 하위 전체를 8182로 라우팅하므로 라우트 추가가 필요 없고,
 * 지도 전용 API 묶음이라는 사실이 URL에도 드러난다.
 */
@Tag(name = "정류장 지도 API", description = "지도 화면 전용 정류소 좌표 조회")
@RestController
@RequestMapping("/api/v1/stop/map")
class StopMapController(
    private val stopMapQueryService: StopMapQueryService,
) {

    /**
     * 좌표를 `String` 으로 받는 이유:
     * `BigDecimal` 로 직접 바인딩하면 형식 오류가 스프링 바인딩 단계에서 터져
     * `ExceptionAdvisor` 가 아니라 프레임워크 기본 메시지로 응답된다.
     * `Coordinate` 검증을 거쳐 도메인 메시지로 400을 내려주기 위해 문자열로 받는다.
     *
     * 줌 레벨(`level`) 파라미터는 **의도적으로 받지 않는다.**
     * 서버 사이드 그리드 집계가 아직 없어 동작에 영향을 주지 않는 파라미터를 스펙에 노출하면
     * 프론트가 이미 지원되는 기능으로 오해한다. 집계 도입 시 함께 추가한다.
     */
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
