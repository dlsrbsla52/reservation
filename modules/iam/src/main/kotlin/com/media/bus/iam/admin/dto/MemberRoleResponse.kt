package com.media.bus.iam.admin.dto

import com.media.bus.iam.auth.entity.PermissionEntity
import com.media.bus.iam.auth.entity.RoleEntity
import io.swagger.v3.oas.annotations.media.Schema
import java.util.*

/** 회원-역할 응답 DTO (역할 및 권한 포함) */
@Schema(description = "회원-역할 응답 DTO (역할 및 권한 포함)")
data class MemberRoleResponse(

    @Schema(description = "회원 ID")
    val memberId: UUID,

    @Schema(description = "역할 정보")
    val role: RoleResponse,

    @Schema(description = "역할에 할당된 권한 목록")
    val permissions: List<PermissionResponse>,
) {
    companion object {
        fun of(memberId: UUID, role: RoleEntity, permissions: List<PermissionEntity>): MemberRoleResponse =
            MemberRoleResponse(
                memberId = memberId,
                role = RoleResponse.of(role),
                permissions = permissions.map { PermissionResponse.of(it) },
            )
    }
}
