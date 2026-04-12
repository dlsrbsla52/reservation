package com.media.bus.iam.auth.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

/**
 * ## 비밀번호 변경 요청 DTO
 *
 * 로그인된 사용자가 **현재 비밀번호**로 본인 확인 후 **새 비밀번호**로 직접 변경할 때 사용한다.
 * 비밀번호 초기화(`PasswordResetConfirmRequest`)와 달리 이메일 토큰이 아닌 현재 비밀번호로 본인 검증을 수행한다.
 */
@Schema(description = "비밀번호 변경 요청 DTO")
data class PasswordChangeRequest(

    @param:Schema(description = "현재 비밀번호", example = "CurrentPassword123!")
    @field:NotBlank(message = "현재 비밀번호를 입력해주세요.")
    val currentPassword: String,

    @param:Schema(description = "새 비밀번호 (영문, 숫자, 특수문자를 각각 1개 이상 포함한 8자 이상)", example = "NewPassword123!")
    @field:NotBlank(message = "새 비밀번호를 입력해주세요.")
    @field:Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.")
    @field:Pattern(
        regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@\$!%*#?&])[A-Za-z\\d@\$!%*#?&]+$",
        message = "비밀번호는 영문, 숫자, 특수문자를 각각 1개 이상 포함해야 합니다.",
    )
    val newPassword: String,
)
