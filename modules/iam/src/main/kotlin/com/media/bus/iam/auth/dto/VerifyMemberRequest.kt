package com.media.bus.iam.auth.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

@Schema(description = "2차 인증 요청 DTO")
data class VerifyMemberRequest (

    @param:Schema(description = "비밀번호", example = "Password123!")
    @field:NotBlank(message = "비밀번호를 입력해주세요.")
    val password: String,
)