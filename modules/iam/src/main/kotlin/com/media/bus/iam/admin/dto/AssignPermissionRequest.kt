package com.media.bus.iam.admin.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

/** 역할에 권한 할당 요청 DTO */
@Schema(description = "역할에 권한 할당 요청 DTO")
data class AssignPermissionRequest(

    @param:Schema(description = "권한 이름 (READ, WRITE, DELETE, MANAGE)", example = "READ")
    @field:NotBlank(message = "권한 이름을 입력해주세요.")
    val permissionName: String,
)
