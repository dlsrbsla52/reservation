package com.media.bus.iam.admin.dto

import com.media.bus.iam.auth.entity.RoleEntity
import io.swagger.v3.oas.annotations.media.Schema
import java.util.*

/** 역할 응답 DTO */
@Schema(description = "역할 응답 DTO")
data class RoleResponse(

    @param:Schema(description = "역할 ID")
    val id: UUID,

    @param:Schema(description = "역할 이름 (MemberType 이름)", example = "ADMIN_USER")
    val name: String,

    @param:Schema(description = "역할 전시명", example = "관리회원 일반")
    val displayName: String,
) {
    companion object {
        fun of(entity: RoleEntity): RoleResponse =
            RoleResponse(
                id = entity.id.value,
                name = entity.name,
                displayName = entity.displayName,
            )
    }
}
