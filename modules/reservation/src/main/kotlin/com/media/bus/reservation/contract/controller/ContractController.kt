package com.media.bus.reservation.contract.controller

import com.media.bus.common.web.response.ApiResponse
import com.media.bus.common.web.wrapper.PageResult
import com.media.bus.contract.security.MemberPrincipal
import com.media.bus.contract.security.annotation.Authorize
import com.media.bus.contract.security.annotation.CurrentMember
import com.media.bus.reservation.contract.dto.response.ContractResponse
import com.media.bus.reservation.contract.service.ContractFacade
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.*
import java.util.*

@Tag(name = "계약 API", description = "회원 본인의 정류소 광고 계약 조회 API")
@RestController
@RequestMapping("/api/v1/contract")
class ContractController(
    private val facade: ContractFacade,
) {
    // 계약 생성은 관리자 기능이라 여기 두지 않는다.
    // 관리자 사이트 → iam `/api/v1/admin/contract` → S2S → `InternalContractController` 경로만 쓴다.

    /** 내 계약 목록 조회 (페이지네이션). 본인 계약만 반환. */
    @Authorize
    @Operation(summary = "내 계약 목록 조회", description = "로그인한 사용자의 계약 목록을 페이지 단위로 조회합니다. 정류소 정보가 결합되어 반환됩니다.")
    @GetMapping("/me")
    fun getMyContracts(
        @CurrentMember principal: MemberPrincipal,
        @Parameter(description = "0-base 페이지 번호") @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "20") size: Int,
    ): ApiResponse<PageResult<ContractResponse>> =
        ApiResponse.success(facade.getMyContracts(principal, page, size))

    /** 계약 단건 상세 조회. 본인 계약만 접근 가능. */
    @Authorize
    @Operation(summary = "계약 단건 상세 조회", description = "계약 ID로 상세 정보를 조회합니다. 본인 계약만 접근 가능하며, 정류소 정보가 결합되어 반환됩니다.")
    @GetMapping("/{contractId}")
    fun getContractDetail(
        @CurrentMember principal: MemberPrincipal,
        @PathVariable contractId: UUID,
    ): ApiResponse<ContractResponse> =
        ApiResponse.success(facade.getContractDetail(principal, contractId))
}
