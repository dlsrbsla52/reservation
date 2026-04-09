package com.media.bus.iam.auth.dto

import com.media.bus.contract.entity.member.MemberType
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.*

/**
 * 회원가입 요청 DTO.
 * - 일반 회원(MEMBER): businessNumber 불필요
 * - 비즈니스 회원(BUSINESS): businessNumber 필수
 * Cross-Field 검증은 AuthService 계층에서 수행한다.
 */
@Schema(description = "회원가입 요청 DTO")
data class RegisterRequest(

    @Schema(description = "로그인 아이디 (4자 이상 50자 이하, 영문/숫자/언더스코어만 허용)", example = "user123")
    @field:NotBlank(message = "아이디를 입력해주세요.")
    @field:Size(min = 4, max = 50, message = "아이디는 4자 이상 50자 이하로 입력해주세요.")
    @field:Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "아이디는 영문, 숫자, 언더스코어(_)만 사용 가능합니다.")
    val loginId: String,

    @Schema(description = "비밀번호 (영문, 숫자, 특수문자를 각각 1개 이상 포함한 8자 이상)", example = "Password123!")
    @field:NotBlank(message = "비밀번호를 입력해주세요.")
    @field:Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.")
    @field:Pattern(
        regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@\$!%*#?&])[A-Za-z\\d@\$!%*#?&]+$",
        message = "비밀번호는 영문, 숫자, 특수문자를 각각 1개 이상 포함해야 합니다.",
    )
    val password: String,

    @Schema(description = "사용자 이메일 주소", example = "user@example.com")
    @field:NotBlank(message = "이메일을 입력해주세요.")
    @field:Email(message = "올바른 이메일 형식을 입력해주세요.")
    val email: String,

    @Schema(description = "핸드폰 번호 (하이픈 제외, 숫자만)", example = "01012345678")
    @field:NotBlank(message = "핸드폰 번호를 입력해주세요.")
    @field:Pattern(regexp = "^01[016789]\\d{7,8}$", message = "올바른 핸드폰 번호를 입력해주세요. (예: 01012345678)")
    val phoneNumber: String,

    @Schema(description = "회원 유형 (MEMBER 또는 BUSINESS)", example = "MEMBER")
    @field:NotNull(message = "회원 유형을 선택해주세요.")
    val memberType: MemberType,

    @Schema(description = "비즈니스 회원 전용 사업자등록번호", example = "123-45-67890", nullable = true)
    val businessNumber: String? = null,
)
