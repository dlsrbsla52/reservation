package com.media.bus.iam.admin.controller

import com.media.bus.common.web.response.ApiResponse
import com.media.bus.contract.entity.member.MemberType
import com.media.bus.contract.entity.member.Permission
import com.media.bus.contract.security.annotation.Authorize
import com.media.bus.iam.admin.dto.AdminMemberContractListResponse
import com.media.bus.iam.admin.facade.AdminBusinessContractFacade
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.*
import java.util.*

/**
 * ## 어드민 사이트 전용 매니저 계약 조회 컨트롤러
 *
 * 일반 사용자 계약 API(`/api/v1/contract/me`)는 본인 계약만 열람할 수 있지만,
 * 어드민은 임의 매니저의 계약 현황을 조회할 수 있어야 한다.
 *
 * `AdminBusinessContractFacade`가 reservation 모듈의 내부 API(S2S) 응답과
 * iam DB의 매니저 정보를 결합하여 응답을 구성한다.
 */
@Tag(
    name = "어드민 매니저 계약 관리",
    description = "어드민 사이트 전용 매니저 계약 조회 API. ADMIN_MASTER/ADMIN_DEVELOPER + MANAGE 권한 필요.",
)
@RestController
@Authorize(types = [MemberType.ADMIN_MASTER, MemberType.ADMIN_DEVELOPER], permissions = [Permission.MANAGE])
@RequestMapping("/api/v1/admin/contract")
class AdminBusinessContractController(
    private val adminBusinessContractFacade: AdminBusinessContractFacade,
) {

    /**
     * 특정 매니저의 계약 목록을 페이지 단위로 조회한다.
     * 매니저 요약 정보와 정류소 정보가 결합된 계약 목록을 함께 반환한다.
     */
    @Operation(
        summary = "매니저 계약 목록 조회",
        description = """
            특정 매니저(memberId)의 계약 목록을 페이지 단위로 조회한다.
            매니저 요약 정보와 정류소 정보가 결합된 계약 목록을 함께 반환한다.
            """,
    )
    @GetMapping("/member/{memberId}")
    fun getMemberContracts(
        @PathVariable memberId: UUID,
        @Parameter(description = "0-base 페이지 번호") @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "20") size: Int,
    ): ApiResponse<AdminMemberContractListResponse> =
        ApiResponse.success(adminBusinessContractFacade.getContractByMemberId(memberId, page, size))
}
