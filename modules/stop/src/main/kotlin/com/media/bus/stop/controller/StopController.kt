package com.media.bus.stop.controller

import com.media.bus.common.web.response.ApiResponse
import com.media.bus.common.web.wrapper.PageResult
import com.media.bus.contract.entity.member.MemberCategory
import com.media.bus.contract.security.annotation.Authorize
import com.media.bus.stop.dto.request.StopSearchCriteria
import com.media.bus.stop.dto.response.BusStopResponse
import com.media.bus.stop.service.StopService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.*

@Tag(name = "정류장 API", description = "버스 정류장의 정보 등과 관련된 API")
@RestController
@RequestMapping("/api/v1/stop")
class StopController(
    private val stopService: StopService,
) {

    @Operation(
        summary = "정류소 목록/조회",
        description = "식별자(pk/stopId/stopName) 중 하나가 있으면 해당 기준으로 단건/동명 조회한다. " +
            "식별자가 없으면 전체 정류소를 페이지네이션으로 반환하며, " +
            "keyword(정류소명·번호 부분 일치)와 stopsType(정류소 유형)으로 필터링할 수 있다.",
    )
    @Authorize(categories = [MemberCategory.USER, MemberCategory.BUSINESS, MemberCategory.ADMIN])
    @GetMapping
    fun getBusStop(
        @RequestParam(required = false) pk: UUID?,
        @RequestParam(required = false) stopId: String?,
        @RequestParam(required = false) stopName: String?,
        @RequestParam(required = false) keyword: String?,
        @RequestParam(required = false) stopsType: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ApiResponse<PageResult<BusStopResponse>> {
        val hasCriteria = pk != null || stopId != null || stopName != null
        return if (hasCriteria) {
            val list = stopService.getBusStop(StopSearchCriteria.of(pk?.let { setOf(it) }, stopId, stopName))
            ApiResponse.success(
                PageResult(items = list, totalCnt = list.size.toLong(), pageRows = size, pageNum = 0),
            )
        } else {
            ApiResponse.success(stopService.getBusStopPage(page, size, keyword, stopsType))
        }
    }
}
