package com.media.bus.iam.admin.controller

import com.media.bus.common.web.response.ApiResponse
import com.media.bus.iam.admin.dto.EffectiveCommissionRateResponse
import com.media.bus.iam.admin.facade.AdminManagerCommissionFacade
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.*

/**
 * ## 정산 비율 S2S 내부 API 컨트롤러
 *
 * `/api/v1/commission/internal/` 하위 경로는 `S2STokenFilter`로 보호된다.
 * Reservation 모듈이 계약 정산 시 최종 적용 비율을 조회하기 위해 호출한다.
 */
@Tag(
    name = "정산 비율 내부 API",
    description = "S2S 전용 정산 비율 조회 API. S2S 토큰 인증 필요.",
)
@RestController
@RequestMapping("/api/v1/commission/internal")
class CommissionInternalController(
    private val commissionFacade: AdminManagerCommissionFacade,
) {

    /**
     * 계약에 적용할 최종 정산 비율을 반환한다.
     *
     * 우선순위:
     * 1. 계약별 오버라이드 (`contractId` 제공 시)
     * 2. 영업사원 기본율
     * 3. 시스템 기본값 10%
     *
     * @param memberId 계약 담당 영업사원 ID
     * @param contractId 계약 ID (오버라이드 조회 생략 시 생략 가능)
     */
    @Operation(
        summary = "최종 정산 비율 조회 (S2S)",
        description = "계약에 적용할 최종 정산 비율을 반환합니다. 오버라이드 → 기본율 → 시스템 기본값(10%) 순으로 적용됩니다.",
    )
    @GetMapping("/effective-rate")
    fun getEffectiveCommissionRate(
        @RequestParam memberId: UUID,
        @RequestParam(required = false) contractId: UUID?,
    ): ApiResponse<EffectiveCommissionRateResponse> {
        val effectiveRate = commissionFacade.resolveEffectiveRate(memberId, contractId)
        return ApiResponse.success(
            EffectiveCommissionRateResponse(
                memberId = memberId,
                contractId = contractId,
                effectiveRate = effectiveRate,
            )
        )
    }
}
