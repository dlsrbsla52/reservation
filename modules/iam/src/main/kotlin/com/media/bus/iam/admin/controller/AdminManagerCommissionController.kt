package com.media.bus.iam.admin.controller

import com.media.bus.common.web.response.ApiResponse
import com.media.bus.contract.entity.member.MemberType
import com.media.bus.contract.entity.member.Permission
import com.media.bus.contract.security.annotation.Authorize
import com.media.bus.iam.admin.dto.*
import com.media.bus.iam.admin.facade.AdminManagerCommissionFacade
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*
import java.util.*

/**
 * ## 어드민 사이트 전용 매니저 수수료 및 정산 컨트롤러
 *
 * 일반 사용자 계약 API(`/api/v1/commission/me`)는 본인 채결한 계약의 수수료만 확인 할 수 있지만,
 * 어드민은 임의 매니저의 수수료 및 정산 현황을 조회 하고 수정할 수 있어야 한다.
 *
 */
@Tag(
    name = "어드민 매니저 수수료 및 정산 관리",
    description = "어드민 사이트 전용 매니저의 수수료 및 정산 관리 API. ADMIN_MASTER/ADMIN_DEVELOPER + MANAGE 권한 필요.",
)
@RestController
@Authorize(types = [MemberType.ADMIN_MASTER, MemberType.ADMIN_DEVELOPER], permissions = [Permission.MANAGE])
@RequestMapping("/api/v1/admin/commission")
class AdminManagerCommissionController(
    private val commissionFacade: AdminManagerCommissionFacade,
) {

    // ─────────────────────────────────────────────────────────────────
    // 영업사원 기본 정산 비율
    // ─────────────────────────────────────────────────────────────────

    /** 영업사원의 기본 정산 비율을 조회한다. */
    @Operation(summary = "영업사원 기본 정산 비율 조회", description = "특정 영업사원의 현재 기본 정산 비율을 조회합니다.")
    @GetMapping("/{memberId}")
    fun getManagerCommission(
        @PathVariable memberId: UUID,
    ): ApiResponse<ManagerCommissionResponse> =
        ApiResponse.success(ManagerCommissionResponse.of(commissionFacade.getManagerCommission(memberId)))

    /**
     * 영업사원의 기본 정산 비율을 수정한다.
     * 변경 이력이 자동으로 기록된다.
     */
    @Operation(summary = "영업사원 기본 정산 비율 수정", description = "특정 영업사원의 기본 정산 비율을 수정합니다. 변경 이력이 기록됩니다.")
    @PutMapping("/{memberId}")
    fun updateManagerCommission(
        @RequestHeader("X-User-Id") requesterId: String,
        @PathVariable memberId: UUID,
        @RequestBody @Valid request: UpdateManagerCommissionRateRequest,
    ): ApiResponse<ManagerCommissionResponse> {
        val updated = commissionFacade.updateManagerCommissionRate(
            memberId = memberId,
            newRate = request.rate,
            changedBy = UUID.fromString(requesterId),
            reason = request.reason,
        )
        return ApiResponse.success(ManagerCommissionResponse.of(updated))
    }

    /** 영업사원의 정산 비율 변경 이력을 최신순으로 조회한다. */
    @Operation(summary = "영업사원 정산 비율 변경 이력 조회", description = "특정 영업사원의 정산 비율 변경 이력 전체(기본율+오버라이드)를 최신순으로 조회합니다.")
    @GetMapping("/{memberId}/history")
    fun getCommissionHistory(
        @PathVariable memberId: UUID,
    ): ApiResponse<List<CommissionRateHistoryResponse>> =
        ApiResponse.success(commissionFacade.getCommissionHistory(memberId).map { CommissionRateHistoryResponse.of(it) })

    // ─────────────────────────────────────────────────────────────────
    // 계약별 정산 비율 오버라이드
    // ─────────────────────────────────────────────────────────────────

    /** 계약의 정산 비율 오버라이드를 조회한다. 오버라이드가 없으면 null을 반환한다. */
    @Operation(summary = "계약별 정산 비율 오버라이드 조회", description = "특정 계약의 정산 비율 오버라이드를 조회합니다. 없으면 data가 null입니다.")
    @GetMapping("/contract/{contractId}/override")
    fun getContractCommissionOverride(
        @PathVariable contractId: UUID,
    ): ApiResponse<ContractCommissionOverrideResponse?> {
        val override = commissionFacade.getContractCommissionOverride(contractId)
        return ApiResponse.success(override?.let { ContractCommissionOverrideResponse.of(it) })
    }

    /**
     * 계약의 정산 비율 오버라이드를 등록하거나 수정한다.
     * 오버라이드가 없으면 새로 생성하고, 있으면 비율을 갱신한다.
     * 변경 이력이 자동으로 기록된다.
     */
    @Operation(summary = "계약별 정산 비율 오버라이드 등록/수정", description = "특정 계약의 정산 비율을 오버라이드합니다. 없으면 생성, 있으면 수정합니다. 변경 이력이 기록됩니다.")
    @PutMapping("/contract/{contractId}/override")
    fun upsertContractCommissionOverride(
        @RequestHeader("X-User-Id") requesterId: String,
        @PathVariable contractId: UUID,
        @RequestBody @Valid request: UpsertContractCommissionOverrideRequest,
    ): ApiResponse<ContractCommissionOverrideResponse> {
        val override = commissionFacade.upsertContractCommissionOverride(
            contractId = contractId,
            memberId = request.memberId,
            newRate = request.rate,
            changedBy = UUID.fromString(requesterId),
            reason = request.reason,
        )
        return ApiResponse.success(ContractCommissionOverrideResponse.of(override))
    }

    /** 특정 계약의 정산 비율 변경 이력을 최신순으로 조회한다. */
    @Operation(summary = "계약별 정산 비율 변경 이력 조회", description = "특정 계약의 정산 비율 오버라이드 이력을 최신순으로 조회합니다.")
    @GetMapping("/contract/{contractId}/history")
    fun getContractCommissionHistory(
        @PathVariable contractId: UUID,
    ): ApiResponse<List<CommissionRateHistoryResponse>> =
        ApiResponse.success(commissionFacade.getContractCommissionHistory(contractId).map { CommissionRateHistoryResponse.of(it) })
}
