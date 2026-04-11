package com.media.bus.iam.admin.dto

import com.media.bus.iam.auth.entity.PermissionEntity
import com.media.bus.iam.auth.entity.RoleEntity
import io.swagger.v3.oas.annotations.media.Schema
import java.util.*

/** 역할 상세 응답 DTO (연결된 권한 포함) */
@Schema(description = "역할 상세 응답 DTO (연결된 권한 포함)")
data class RoleDetailResponse(

    @Schema(description = "역할 ID")
    val id: UUID,

    @Schema(description = "역할 이름 (MemberType 이름)", example = "ADMIN_MASTER")
    val name: String,

    @Schema(description = "역할 전시명", example = "관리회원 마스터")
    val displayName: String,

    @Schema(description = "역할에 할당된 권한 목록")
    val permissions: List<PermissionResponse>,
) {
    companion object {
        fun of(role: RoleEntity, permissions: List<PermissionEntity>): RoleDetailResponse =
            RoleDetailResponse(
                id = role.id.value,
                name = role.name,
                displayName = role.displayName,
                permissions = permissions.map { PermissionResponse.of(it) },
            )
    }
}
