package com.media.bus.stop.controller

import com.media.bus.common.web.response.ApiResponse
import com.media.bus.common.web.response.ListData
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

/**
 * ## MSA 내부 서비스 간 통신 전용 정류장 컨트롤러
 *
 * 외부 노출 금지 — `SecurityConfig`에 등록된 `S2STokenFilter`가
 * /api/v1/internal 하위 전체 경로를 보호합니다.
 * `@Authorize` 없이 S2S 토큰(`X-Service-Token`) 검증만으로 접근을 허용합니다.
 *
 * 설계 의도:
 * - 사용자 노출 API(`/api/v1/stop`)는 ADMIN 카테고리 회원만 접근 가능
 * - 내부 서비스(reservation 등)는 회원 컨텍스트 없이 S2S 토큰으로 호출
 * - 두 관심사를 분리하여 `@Authorize` 인가 로직이 S2S 흐름에 개입하지 않도록 합니다
 */
@Tag(name = "정류장 내부 API", description = "MSA 내부 서비스 간 통신 전용 — 외부 호출 금지, S2S 토큰 필수")
@RestController
@RequestMapping("/api/v1/internal/stop")
class InternalStopController(
    private val stopService: StopService,
) {

    @Operation(
        summary = "정류소 조회 (내부 전용)",
        description = "pk(UUID), stopId(정류소 번호), stopName(이름 완전 일치) 중 하나를 쿼리 파라미터로 전달. 우선순위: pk > stopId > stopName. stopName은 동명 정류소 시 다건 반환",
    )
    @GetMapping
    fun getBusStop(
        @RequestParam(required = false) pk: UUID?,
        @RequestParam(required = false) stopId: String?,
        @RequestParam(required = false) stopName: String?,
    ): ApiResponse<ListData<BusStopResponse>> =
        ApiResponse.page(stopService.getBusStop(StopSearchCriteria.of(pk, stopId, stopName)))
}
