package com.media.bus.iam.admin.controller

import com.media.bus.common.exceptions.BusinessException
import com.media.bus.common.result.type.CommonResult
import com.media.bus.common.web.response.ApiResponse
import com.media.bus.common.web.wrapper.PageResult
import com.media.bus.contract.entity.member.MemberType
import com.media.bus.contract.entity.member.Permission
import com.media.bus.contract.security.annotation.Authorize
import com.media.bus.iam.admin.dto.AdminBusinessContractResponse
import com.media.bus.iam.admin.dto.AdminContractResponse
import com.media.bus.iam.admin.dto.AdminMemberContractListResponse
import com.media.bus.iam.admin.dto.CreateAdminContractRequest
import com.media.bus.iam.admin.facade.AdminBusinessContractFacade
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.*
import java.time.OffsetDateTime
import java.util.*

/** IAM 어드민에서 reservation 내부 API를 통해 계약을 관리한다. */
@Tag(name = "어드민 계약 관리", description = "어드민 사이트 전용 계약 생성 및 조회 API")
@RestController
@Authorize(types = [MemberType.ADMIN_MASTER, MemberType.ADMIN_DEVELOPER], permissions = [Permission.MANAGE])
@RequestMapping("/api/v1/admin/contract")
class AdminContractController(
    private val facade: AdminBusinessContractFacade,
) {
    companion object {
        private const val MAX_PAGE_SIZE = 100
        private const val MAX_KEYWORD_LENGTH = 100
    }

    @Operation(summary = "전체 계약 검색")
    @GetMapping
    fun searchContracts(
        @RequestParam(required = false) memberId: UUID?,
        @RequestParam(required = false) stopId: UUID?,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) keyword: String?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        contractStartFrom: OffsetDateTime?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        contractStartTo: OffsetDateTime?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ApiResponse<PageResult<AdminBusinessContractResponse>> {
        validateSearchCondition(keyword, contractStartFrom, contractStartTo)
        val safePage = page.coerceAtLeast(0)
        val safeSize = size.coerceIn(1, MAX_PAGE_SIZE)
        return ApiResponse.success(
            facade.searchContracts(
                memberId, stopId, status, keyword, contractStartFrom, contractStartTo, safePage, safeSize,
            ),
        )
    }

    private fun validateSearchCondition(
        keyword: String?,
        contractStartFrom: OffsetDateTime?,
        contractStartTo: OffsetDateTime?,
    ) {
        if (keyword != null && keyword.length > MAX_KEYWORD_LENGTH) {
            throw BusinessException(CommonResult.VALIDATION_FAIL, "keyword는 ${MAX_KEYWORD_LENGTH}자 이하여야 합니다.")
        }
        if (contractStartFrom != null && contractStartTo != null && contractStartFrom > contractStartTo) {
            throw BusinessException(CommonResult.VALIDATION_FAIL, "contractStartFrom은 contractStartTo 이후일 수 없습니다.")
        }
    }
    @Operation(summary = "계약 생성")
    @PostMapping
    fun createContract(
        @RequestBody @Valid request: CreateAdminContractRequest,
    ): ApiResponse<AdminContractResponse> = ApiResponse.success(facade.createContract(request))

    @Operation(summary = "매니저 계약 목록 조회")
    @GetMapping("/member/{memberId}")
    fun getMemberContracts(
        @PathVariable memberId: UUID,
        @Parameter(description = "0-base 페이지 번호") @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "20") size: Int,
    ): ApiResponse<AdminMemberContractListResponse> =
        ApiResponse.success(
            facade.getContractByMemberId(
                memberId,
                page.coerceAtLeast(0),
                size.coerceIn(1, MAX_PAGE_SIZE),
            ),
        )

    @Operation(summary = "계약 상세 조회")
    @GetMapping("/{contractId}")
    fun getContractDetail(
        @PathVariable contractId: UUID,
    ): ApiResponse<AdminBusinessContractResponse> = ApiResponse.success(facade.getContractDetail(contractId))
}
