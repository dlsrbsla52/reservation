package com.media.bus.iam.admin.controller

import com.media.bus.common.web.response.ApiResponse
import com.media.bus.common.web.wrapper.PageResult
import com.media.bus.contract.entity.member.MemberCategory
import com.media.bus.contract.entity.member.MemberType
import com.media.bus.contract.entity.member.Permission
import com.media.bus.contract.security.annotation.Authorize
import com.media.bus.iam.admin.dto.*
import com.media.bus.iam.admin.facade.AdminMemberFacade
import com.media.bus.iam.admin.service.MemberSearchService
import com.media.bus.iam.member.dto.MemberSearchCondition
import com.media.bus.iam.member.entity.enumerated.MemberStatus
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.time.OffsetDateTime
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
    private val adminMemberFacade: AdminMemberFacade,
    private val memberSearchService: MemberSearchService,
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
        ApiResponse.success(adminMemberFacade.createAdminMember(request))

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
        ApiResponse.success(adminMemberFacade.findAllMembers(page, size))

    /** 키워드로 회원을 검색한다. `/members/{memberId}` 보다 먼저 매핑되도록 선언한다. */
    @Operation(summary = "회원 검색", description = "로그인 아이디, 이메일, 이름으로 회원을 검색합니다.")
    @GetMapping("/members/search")
    fun searchMembers(@RequestParam keyword: String): ApiResponse<List<AdminMemberListResponse>> =
        ApiResponse.success(adminMemberFacade.searchMembers(keyword))

    /**
     * 어드민 회원을 조건으로 검색한다. 조회 대상은 ADMIN 카테고리로 고정된다.
     * 권한 부여·실적 조회 등 어드민 계정 관리의 진입점이며, `ADMIN_MASTER`+`MANAGE` 권한이 필요하다.
     */
    @Operation(
        summary = "어드민 회원 조건 검색",
        description = "키워드/상태/유형/가입일 범위로 어드민(ADMIN_*) 회원을 페이지 조회합니다. ADMIN_MASTER + MANAGE 권한 필요.",
    )
    @GetMapping("/admins/search")
    fun searchAdminMembers(
        @RequestParam(required = false) keyword: String?,
        @RequestParam(required = false) status: MemberStatus?,
        @RequestParam(required = false) type: MemberType?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) createdFrom: OffsetDateTime?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) createdTo: OffsetDateTime?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ApiResponse<PageResult<MemberSearchResponse>> {
        val condition = MemberSearchCondition(
            keyword = keyword,
            status = status,
            type = type,
            createdFrom = createdFrom,
            createdTo = createdTo,
            page = page,
            size = size,
        )
        // 어드민 회원만 조회 가능하도록 ADMIN 카테고리로 고정
        return ApiResponse.success(memberSearchService.search(condition, setOf(MemberCategory.ADMIN)))
    }

    /** 회원 상세 정보를 조회한다 (역할 및 권한 포함). */
    @Operation(summary = "회원 상세 조회", description = "특정 회원의 상세 정보를 역할 및 권한과 함께 조회합니다.")
    @GetMapping("/members/{memberId}")
    fun findMemberDetail(@PathVariable memberId: UUID): ApiResponse<AdminMemberDetailResponse> =
        ApiResponse.success(adminMemberFacade.findMemberDetail(memberId))

    /** 회원 계정을 정지한다. 사유를 필수로 입력받는다. */
    @Operation(summary = "회원 정지", description = "대상 회원의 계정을 정지합니다. 정지 사유를 필수로 입력해야 합니다.")
    @PutMapping("/members/{memberId}/suspend")
    fun suspendMember(
        @RequestHeader("X-User-Id") requesterId: String,
        @PathVariable memberId: UUID,
        @RequestBody @Valid request: SuspendMemberRequest,
    ): ApiResponse<Unit?> {
        adminMemberFacade.suspendMember(UUID.fromString(requesterId), memberId, request.reason)
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
        adminMemberFacade.unsuspendMember(UUID.fromString(requesterId), memberId, request.reason)
        return ApiResponse.successWithMessage("회원 계정 정지가 해제되었습니다.")
    }

    /** 특정 회원의 상태 변경 이력을 조회한다. */
    @Operation(summary = "회원 상태 변경 이력 조회", description = "특정 회원의 정지/해제 이력을 최신순으로 조회합니다.")
    @GetMapping("/members/{memberId}/status-history")
    fun findMemberStatusHistory(@PathVariable memberId: UUID): ApiResponse<List<MemberStatusHistoryResponse>> =
        ApiResponse.success(adminMemberFacade.findMemberStatusHistory(memberId))
}
