package com.media.bus.iam.admin.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

/** 회원 역할 변경 요청 DTO */
@Schema(description = "회원 역할 변경 요청 DTO")
data class ChangeMemberRoleRequest(

    @param:Schema(description = "역할 이름 (MemberType 이름)", example = "ADMIN_USER")
    @field:NotBlank(message = "역할 이름을 입력해주세요.")
    val roleName: String,
)
