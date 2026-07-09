package com.media.bus.stop.controller

import com.media.bus.common.web.response.ApiResponse
import com.media.bus.common.web.response.ListData
import com.media.bus.stop.dto.request.BulkStopLookupRequest
import com.media.bus.stop.dto.request.CreateStopPriceRequest
import com.media.bus.stop.dto.request.StopSearchCriteria
import com.media.bus.stop.dto.request.UpdateStopPriceRequest
import com.media.bus.stop.dto.response.BusStopResponse
import com.media.bus.stop.dto.response.StopPriceResponse
import com.media.bus.stop.service.StopPriceService
import com.media.bus.stop.service.StopService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*
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
    private val stopPriceService: StopPriceService,
) {

    @Operation(
        summary = "정류소 조회 (내부 전용)",
        description = "pk(UUID), stopId(정류소 번호), stopName(이름 완전 일치) 중 하나를 쿼리 파라미터로 전달. 우선순위: pk > stopId > stopName. stopName은 동명 정류소 시 다건 반환",
    )
    @GetMapping
    fun getBusStop(
        @RequestParam(required = false) pk: Set<UUID>?,
        @RequestParam(required = false) stopId: String?,
        @RequestParam(required = false) stopName: String?,
    ): ApiResponse<ListData<BusStopResponse>> =
        ApiResponse.page(stopService.getBusStop(StopSearchCriteria.of(pk, stopId, stopName)))

    /**
     * 정류소 일괄 조회 — 예약 서비스의 목록 조회 시 N+1 호출 제거용.
     * 입력 id 중 존재하지 않는 것은 결과에서 누락되며, 호출자는 누락된 id를 fallback 처리해야 한다.
     */
    @Operation(
        summary = "정류소 일괄 조회 (내부 전용)",
        description = "pk(UUID) 목록을 body로 전달하여 정류소를 한 번에 조회합니다. 최대 200개, 누락 id는 결과에서 제외됩니다.",
    )
    @PostMapping("/bulk")
    fun getBusStopsBulk(
        @RequestBody @Valid request: BulkStopLookupRequest,
    ): ApiResponse<ListData<BusStopResponse>> =
        ApiResponse.page(stopService.getBusStopsByIds(request.ids))

    // ─────────────────────────────────────────────────────────────────
    // 정류소 단가 (내부 전용)
    // ─────────────────────────────────────────────────────────────────

    @Operation(
        summary = "정류소 단가 조회 (내부 전용)",
        description = "정류소 pk(UUID) 기준 현재 단가를 조회합니다. 등록된 단가가 없으면 data가 null인 성공 응답을 반환합니다.",
    )
    @GetMapping("/price/{stopId}")
    fun getStopPrice(@PathVariable stopId: UUID): ApiResponse<StopPriceResponse?> =
        ApiResponse.success(stopPriceService.getPrice(stopId))

    @Operation(
        summary = "정류소 단가 등록 (내부 전용)",
        description = "정류소당 단가 1건을 신규 등록합니다. 이미 등록된 정류소는 409 CONFLICT를 반환합니다.",
    )
    @PostMapping("/price")
    fun createStopPrice(
        @RequestBody @Valid request: CreateStopPriceRequest,
    ): ApiResponse<StopPriceResponse> =
        ApiResponse.success(
            stopPriceService.createPrice(request.stopId, request.unitPrice, request.registeredById),
        )

    @Operation(
        summary = "정류소 단가 수정 (내부 전용)",
        description = "등록된 정류소 단가를 변경합니다. 등록된 단가가 없으면 404 NOT_FOUND를 반환합니다.",
    )
    @PutMapping("/price/{stopId}")
    fun updateStopPrice(
        @PathVariable stopId: UUID,
        @RequestBody @Valid request: UpdateStopPriceRequest,
    ): ApiResponse<StopPriceResponse> =
        ApiResponse.success(
            stopPriceService.updatePrice(stopId, request.unitPrice, request.changedById),
        )

    @Operation(
        summary = "정류소 단가 삭제 (내부 전용)",
        description = "등록된 정류소 단가를 삭제합니다. 등록된 단가가 없으면 404 NOT_FOUND를 반환합니다.",
    )
    @DeleteMapping("/price/{stopId}")
    fun deleteStopPrice(
        @PathVariable stopId: UUID,
        @RequestParam(required = false) changedById: UUID?,
    ): ApiResponse<Unit?> {
        stopPriceService.deletePrice(stopId, changedById)
        return ApiResponse.successWithMessage("정류소 단가가 삭제되었습니다.")
    }
}
