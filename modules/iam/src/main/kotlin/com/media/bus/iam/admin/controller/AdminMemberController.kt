package com.media.bus.iam.admin.controller

import com.media.bus.common.web.response.ApiResponse
import com.media.bus.common.web.wrapper.PageResult
import com.media.bus.contract.entity.member.MemberType
import com.media.bus.contract.entity.member.Permission
import com.media.bus.contract.security.annotation.Authorize
import com.media.bus.iam.admin.dto.*
import com.media.bus.iam.admin.service.AdminMemberService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.util.*

/**
 * ## 어드민 사이트 전용 멤버 관리 컨트롤러
 *
 * `ADMIN_MASTER` + `MANAGE` 권한을 가진 사용자(manager)만 접근 가능하다.
 */
@Tag(name = "어드민 멤버 관리 API", description = "어드민 사이트 전용 멤버 생성 API. ADMIN_MASTER + MANAGE 권한 필요.")
@RestController
@RequestMapping("/api/v1/admin")
@Authorize(types = [MemberType.ADMIN_MASTER], permissions = [Permission.MANAGE])
class AdminMemberController(
    private val adminMemberService: AdminMemberService,
) {
    /**
     * 어드민 멤버 생성.
     * `ADMIN_MASTER` + `MANAGE` 권한 보유 시에만 호출 가능하다.
     * 생성된 어드민 계정은 `emailVerified = true` 상태로 즉시 활성화된다.
     */
    @Operation(
        summary = "어드민 멤버 생성",
        description = "어드민 사이트에서 새 어드민 계정을 생성합니다. ADMIN_MASTER + MANAGE 권한 필요.",
    )
    @PostMapping("/members")
    @ResponseStatus(HttpStatus.CREATED)
    fun createAdminMember(@RequestBody @Valid request: CreateAdminMemberRequest): ApiResponse<AdminMemberResponse> =
        ApiResponse.success(adminMemberService.createAdminMember(request))

    // ─────────────────────────────────────────────────────────────────
    // 회원 관리 (조회, 검색, 정지, 해제)
    // ─────────────────────────────────────────────────────────────────

    /** 전체 회원 목록을 페이지네이션으로 조회한다. */
    @Operation(summary = "회원 목록 조회", description = "전체 회원 목록을 페이지네이션으로 조회합니다.")
    @GetMapping("/members")
    fun findAllMembers(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ApiResponse<PageResult<AdminMemberListResponse>> =
        ApiResponse.success(adminMemberService.findAllMembers(page, size))

    /** 키워드로 회원을 검색한다. `/members/{memberId}` 보다 먼저 매핑되도록 선언한다. */
    @Operation(summary = "회원 검색", description = "로그인 아이디, 이메일, 이름으로 회원을 검색합니다.")
    @GetMapping("/members/search")
    fun searchMembers(@RequestParam keyword: String): ApiResponse<List<AdminMemberListResponse>> =
        ApiResponse.success(adminMemberService.searchMembers(keyword))

    /** 회원 상세 정보를 조회한다 (역할 및 권한 포함). */
    @Operation(summary = "회원 상세 조회", description = "특정 회원의 상세 정보를 역할 및 권한과 함께 조회합니다.")
    @GetMapping("/members/{memberId}")
    fun findMemberDetail(@PathVariable memberId: UUID): ApiResponse<AdminMemberDetailResponse> =
        ApiResponse.success(adminMemberService.findMemberDetail(memberId))

    /** 회원 계정을 정지한다. 사유를 필수로 입력받는다. */
    @Operation(summary = "회원 정지", description = "대상 회원의 계정을 정지합니다. 정지 사유를 필수로 입력해야 합니다.")
    @PutMapping("/members/{memberId}/suspend")
    fun suspendMember(
        @RequestHeader("X-User-Id") requesterId: String,
        @PathVariable memberId: UUID,
        @RequestBody @Valid request: SuspendMemberRequest,
    ): ApiResponse<Unit?> {
        adminMemberService.suspendMember(UUID.fromString(requesterId), memberId, request.reason)
        return ApiResponse.successWithMessage("회원 계정이 정지되었습니다.")
    }

    /** 정지된 회원의 계정을 해제한다. 해제 사유를 필수로 입력받는다. */
    @Operation(summary = "회원 정지 해제", description = "정지된 회원의 계정을 다시 활성화합니다. 해제 사유를 필수로 입력해야 합니다.")
    @PutMapping("/members/{memberId}/unsuspend")
    fun unsuspendMember(
        @RequestHeader("X-User-Id") requesterId: String,
        @PathVariable memberId: UUID,
        @RequestBody @Valid request: UnsuspendMemberRequest,
    ): ApiResponse<Unit?> {
        adminMemberService.unsuspendMember(UUID.fromString(requesterId), memberId, request.reason)
        return ApiResponse.successWithMessage("회원 계정 정지가 해제되었습니다.")
    }

    /** 특정 회원의 상태 변경 이력을 조회한다. */
    @Operation(summary = "회원 상태 변경 이력 조회", description = "특정 회원의 정지/해제 이력을 최신순으로 조회합니다.")
    @GetMapping("/members/{memberId}/status-history")
    fun findMemberStatusHistory(@PathVariable memberId: UUID): ApiResponse<List<MemberStatusHistoryResponse>> =
        ApiResponse.success(adminMemberService.findMemberStatusHistory(memberId))
}
