package com.media.bus.iam.auth.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

/** 비밀번호 초기화 요청 DTO — loginId 또는 email 중 하나 필수 */
@Schema(description = "비밀번호 초기화 요청 DTO")
data class PasswordResetRequest(

    @param:Schema(description = "로그인 아이디", example = "user123", nullable = true)
    val loginId: String? = null,

    @param:Schema(description = "이메일 주소", example = "user@example.com", nullable = true)
    val email: String? = null,
)

/** 비밀번호 초기화 토큰 유효성 확인 DTO */
@Schema(description = "비밀번호 초기화 토큰 검증 DTO")
data class PasswordResetVerifyRequest(

    @param:Schema(description = "비밀번호 초기화 토큰")
    @field:NotBlank(message = "토큰을 입력해주세요.")
    val token: String,
)

/** 비밀번호 초기화 확정 DTO — 토큰 + 새 비밀번호 */
@Schema(description = "비밀번호 초기화 확정 DTO")
data class PasswordResetConfirmRequest(

    @param:Schema(description = "비밀번호 초기화 토큰")
    @field:NotBlank(message = "토큰을 입력해주세요.")
    val token: String,

    @param:Schema(description = "새 비밀번호 (영문, 숫자, 특수문자를 각각 1개 이상 포함한 8자 이상)", example = "NewPassword123!")
    @field:NotBlank(message = "새 비밀번호를 입력해주세요.")
    @field:Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.")
    @field:Pattern(
        regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@\$!%*#?&])[A-Za-z\\d@\$!%*#?&]+$",
        message = "비밀번호는 영문, 숫자, 특수문자를 각각 1개 이상 포함해야 합니다.",
    )
    val newPassword: String,
)
