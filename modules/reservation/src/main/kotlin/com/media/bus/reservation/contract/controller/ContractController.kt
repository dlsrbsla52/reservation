package com.media.bus.reservation.contract.controller

import com.media.bus.common.web.response.ApiResponse
import com.media.bus.common.web.wrapper.PageResult
import com.media.bus.contract.entity.member.MemberCategory
import com.media.bus.contract.entity.member.Permission
import com.media.bus.contract.security.MemberPrincipal
import com.media.bus.contract.security.annotation.Authorize
import com.media.bus.contract.security.annotation.CurrentMember
import com.media.bus.reservation.contract.dto.request.CreateContractRequest
import com.media.bus.reservation.contract.dto.response.ContractResponse
import com.media.bus.reservation.contract.service.ContractFacade
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*
import java.util.*

@Tag(name = "계약 API", description = "정류소 광고 계약 생성 및 관리 API")
@RestController
@RequestMapping("/api/v1/contract")
class ContractController(
    private val facade: ContractFacade,
) {
    /**
     * 계약 생성.
     * - `@Authorize`: JWT 인증 필수, 어드민만 등록 가능(write 권한 필수)
     * - request.memberId가 있으면 IAM에서 해당 회원 존재 여부 검증 (없으면 비회원 계약으로 진행)
     */
    @Authorize(categories = [MemberCategory.ADMIN], permissions = [Permission.WRITE])
    @Operation(summary = "계약 생성", description = "정류소 광고 계약을 생성합니다. memberId가 있으면 IAM DB에서 회원을 검증하며, 없으면 비회원 계약으로 저장합니다.")
    @PostMapping
    fun createContract(
        @CurrentMember principal: MemberPrincipal,
        @RequestBody @Valid request: CreateContractRequest,
    ): ApiResponse<ContractResponse> =
        ApiResponse.success(facade.createContract(principal, request))

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
