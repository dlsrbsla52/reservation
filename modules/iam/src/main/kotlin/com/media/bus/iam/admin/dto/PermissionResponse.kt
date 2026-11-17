package com.media.bus.iam.admin.dto

import com.media.bus.iam.auth.entity.PermissionEntity
import io.swagger.v3.oas.annotations.media.Schema
import java.util.*

/** 권한 응답 DTO */
@Schema(description = "권한 응답 DTO")
data class PermissionResponse(

    @param:Schema(description = "권한 ID")
    val id: UUID,

    @param:Schema(description = "권한 이름", example = "READ")
    val name: String,

    @param:Schema(description = "권한 전시명", example = "조회")
    val displayName: String,
) {
    companion object {
        fun of(entity: PermissionEntity): PermissionResponse =
            PermissionResponse(
                id = entity.id.value,
                name = entity.name,
                displayName = entity.displayName,
            )
    }
}
