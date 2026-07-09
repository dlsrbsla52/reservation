package com.media.bus.iam.admin.controller

import com.media.bus.common.web.response.ApiResponse
import com.media.bus.contract.entity.member.MemberType
import com.media.bus.contract.entity.member.Permission
import com.media.bus.contract.security.annotation.Authorize
import com.media.bus.iam.admin.dto.CreateStopPriceRequest
import com.media.bus.iam.admin.dto.StopPriceResponse
import com.media.bus.iam.admin.dto.UpdateStopPriceRequest
import com.media.bus.iam.admin.facade.AdminStopPriceFacade
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*
import java.util.*

/**
 * ## 어드민 사이트 전용 정류소 단가 관리 컨트롤러
 *
 * 어드민 매니저/마스터 권한을 가진 사용자가 정류소 단가를 제어한다.
 * 내부적으로 S2S API 클라이언트(`StopServiceClient`)를 통해 stop 모듈로 요청을 위임하여 실행한다.
 */
@Tag(
    name = "어드민 정류소 관리",
    description = "어드민 사이트 전용 정류소 가격 관리 API. ADMIN_MASTER/ADMIN_DEVELOPER + MANAGE 권한 필요.",
)
@RestController
@Authorize(types = [MemberType.ADMIN_MASTER, MemberType.ADMIN_DEVELOPER], permissions = [Permission.MANAGE])
@RequestMapping("/api/v1/admin/stop")
class AdminStopPriceController(
    private val adminStopPriceFacade: AdminStopPriceFacade,
) {

    /** 정류소 단가를 조회한다. 등록된 단가가 없으면 data가 null인 성공 응답을 반환한다. */
    @Operation(summary = "정류소 단가 조회", description = "특정 정류소의 현재 단가를 조회합니다.")
    @GetMapping("/{stopId}/price")
    fun getStopPrice(@PathVariable stopId: UUID): ApiResponse<StopPriceResponse?> =
        ApiResponse.success(adminStopPriceFacade.getStopPrice(stopId)?.let(StopPriceResponse::of))

    /** 정류소 단가를 신규 등록한다. 이미 등록된 정류소는 stop 모듈에서 409 CONFLICT를 반환한다. */
    @Operation(summary = "정류소 단가 등록", description = "정류소에 단가를 신규 등록합니다.")
    @PostMapping("/price")
    fun createStopPrice(
        @RequestHeader("X-User-Id") requesterId: String,
        @RequestBody @Valid request: CreateStopPriceRequest,
    ): ApiResponse<StopPriceResponse> =
        ApiResponse.success(
            StopPriceResponse.of(
                adminStopPriceFacade.createStopPrice(request.stopId, request.unitPrice, UUID.fromString(requesterId)),
            ),
        )

    /** 정류소 단가를 수정한다. */
    @Operation(summary = "정류소 단가 수정", description = "정류소의 단가를 수정합니다.")
    @PutMapping("/{stopId}/price")
    fun updateStopPrice(
        @RequestHeader("X-User-Id") requesterId: String,
        @PathVariable stopId: UUID,
        @RequestBody @Valid request: UpdateStopPriceRequest,
    ): ApiResponse<StopPriceResponse> =
        ApiResponse.success(
            StopPriceResponse.of(
                adminStopPriceFacade.updateStopPrice(stopId, request.unitPrice, UUID.fromString(requesterId)),
            ),
        )

    /** 정류소 단가를 삭제한다. */
    @Operation(summary = "정류소 단가 삭제", description = "정류소의 단가를 삭제합니다.")
    @DeleteMapping("/{stopId}/price")
    fun deleteStopPrice(
        @RequestHeader("X-User-Id") requesterId: String,
        @PathVariable stopId: UUID,
    ): ApiResponse<Unit?> {
        adminStopPriceFacade.deleteStopPrice(stopId, UUID.fromString(requesterId))
        return ApiResponse.successWithMessage("정류소 단가가 삭제되었습니다.")
    }
}
