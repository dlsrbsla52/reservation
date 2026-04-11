package com.media.bus.iam.admin.controller

import com.media.bus.common.web.response.ApiResponse
import com.media.bus.contract.entity.member.MemberType
import com.media.bus.contract.entity.member.Permission
import com.media.bus.contract.security.annotation.Authorize
import com.media.bus.iam.admin.dto.*
import com.media.bus.iam.admin.service.AdminAuthorizationService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.util.*

/**
 * ## 어드민 권한 관리 컨트롤러
 *
 * 역할/권한 조회 및 역할-권한 매핑, 회원-역할 관리 API를 제공한다.
 * `ADMIN_MASTER` + `MANAGE` 권한을 가진 사용자만 접근 가능하다.
 */
@Tag(name = "어드민 권한 관리 API", description = "역할/권한 조회 및 관리 API. ADMIN_MASTER + MANAGE 권한 필요.")
@RestController
@RequestMapping("/api/v1/admin/authorization")
@Authorize(types = [MemberType.ADMIN_MASTER], permissions = [Permission.MANAGE])
class AdminAuthorizationController(
    private val adminAuthorizationService: AdminAuthorizationService,
) {

    // ──────────────────────────── 역할 ────────────────────────────

    @Operation(summary = "전체 역할 목록 조회", description = "시스템에 등록된 모든 역할을 조회합니다.")
    @GetMapping("/roles")
    fun getRoles(): ApiResponse<List<RoleResponse>> =
        ApiResponse.success(adminAuthorizationService.findAllRoles())

    @Operation(summary = "역할 상세 조회", description = "역할의 상세 정보와 연결된 권한 목록을 조회합니다.")
    @GetMapping("/roles/{roleId}")
    fun getRoleDetail(@PathVariable roleId: UUID): ApiResponse<RoleDetailResponse> =
        ApiResponse.success(adminAuthorizationService.findRoleDetail(roleId))

    // ──────────────────────────── 권한 ────────────────────────────

    @Operation(summary = "전체 권한 목록 조회", description = "시스템에 등록된 모든 권한을 조회합니다.")
    @GetMapping("/permissions")
    fun getPermissions(): ApiResponse<List<PermissionResponse>> =
        ApiResponse.success(adminAuthorizationService.findAllPermissions())

    // ──────────────────────── 역할-권한 매핑 ────────────────────────

    @Operation(summary = "역할별 권한 조회", description = "지정된 역할에 할당된 권한 목록을 조회합니다.")
    @GetMapping("/roles/{roleId}/permissions")
    fun getRolePermissions(@PathVariable roleId: UUID): ApiResponse<List<PermissionResponse>> =
        ApiResponse.success(adminAuthorizationService.findPermissionsByRoleId(roleId))

    @Operation(summary = "역할에 권한 할당", description = "지정된 역할에 권한을 할당합니다.")
    @PostMapping("/roles/{roleId}/permissions")
    @ResponseStatus(HttpStatus.CREATED)
    fun assignPermission(
        @PathVariable roleId: UUID,
        @RequestBody @Valid request: AssignPermissionRequest,
    ): ApiResponse<Unit?> = ApiResponse.successWith {
        adminAuthorizationService.assignPermissionToRole(roleId, request)
    }

    @Operation(summary = "역할에서 권한 해제", description = "지정된 역할에서 권한을 해제합니다.")
    @DeleteMapping("/roles/{roleId}/permissions/{permissionName}")
    fun revokePermission(
        @PathVariable roleId: UUID,
        @PathVariable permissionName: String,
    ): ApiResponse<Unit?> = ApiResponse.successWith {
        adminAuthorizationService.revokePermissionFromRole(roleId, permissionName)
    }

    // ──────────────────────── 회원-역할 관리 ────────────────────────

    @Operation(summary = "회원 역할/권한 조회", description = "지정된 회원의 역할과 권한 정보를 조회합니다.")
    @GetMapping("/members/{memberId}/role")
    fun getMemberRole(@PathVariable memberId: UUID): ApiResponse<MemberRoleResponse> =
        ApiResponse.success(adminAuthorizationService.findMemberRole(memberId))

    @Operation(summary = "회원 역할 변경", description = "지정된 회원의 역할을 변경합니다.")
    @PutMapping("/members/{memberId}/role")
    fun changeMemberRole(
        @PathVariable memberId: UUID,
        @RequestBody @Valid request: ChangeMemberRoleRequest,
    ): ApiResponse<Unit?> = ApiResponse.successWith {
        adminAuthorizationService.changeMemberRole(memberId, request)
    }
}
