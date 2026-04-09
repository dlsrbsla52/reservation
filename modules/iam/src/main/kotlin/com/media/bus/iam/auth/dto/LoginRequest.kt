package com.media.bus.iam.auth.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

/** 로그인 요청 DTO */
@Schema(description = "로그인 요청 DTO")
data class LoginRequest(

    @Schema(description = "로그인 아이디", example = "user123")
    @field:NotBlank(message = "아이디를 입력해주세요.")
    val loginId: String,

    @Schema(description = "비밀번호", example = "Password123!")
    @field:NotBlank(message = "비밀번호를 입력해주세요.")
    val password: String,
)
