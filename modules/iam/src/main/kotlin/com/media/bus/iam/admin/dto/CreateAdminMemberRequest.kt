package com.media.bus.iam.admin.dto

import com.media.bus.contract.entity.member.MemberType
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.*

/**
 * 어드민 멤버 생성 요청 DTO.
 * `ADMIN_USER`, `ADMIN_MASTER`, `ADMIN_DEVELOPER` 타입만 허용된다.
 * 타입 검증은 `AdminRegisterRequestValidator`에서 수행한다.
 */
@Schema(description = "어드민 멤버 생성 요청 DTO")
data class CreateAdminMemberRequest(

    @Schema(description = "로그인 아이디 (4자 이상 50자 이하, 영문/숫자/언더스코어만 허용)", example = "admin_user01")
    @field:NotBlank(message = "아이디를 입력해주세요.")
    @field:Size(min = 4, max = 50, message = "아이디는 4자 이상 50자 이하로 입력해주세요.")
    @field:Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "아이디는 영문, 숫자, 언더스코어(_)만 사용 가능합니다.")
    val loginId: String,

    @Schema(description = "비밀번호 (영문, 숫자, 특수문자를 각각 1개 이상 포함한 8자 이상)", example = "AdminPass123!")
    @field:NotBlank(message = "비밀번호를 입력해주세요.")
    @field:Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.")
    @field:Pattern(
        regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@\$!%*#?&])[A-Za-z\\d@\$!%*#?&]+$",
        message = "비밀번호는 영문, 숫자, 특수문자를 각각 1개 이상 포함해야 합니다.",
    )
    val password: String,

    @Schema(description = "이메일 주소", example = "admin@example.com")
    @field:NotBlank(message = "이메일을 입력해주세요.")
    @field:Email(message = "올바른 이메일 형식을 입력해주세요.")
    val email: String,

    @Schema(description = "핸드폰 번호 (하이픈 제외, 숫자만)", example = "01012345678")
    @field:NotBlank(message = "핸드폰 번호를 입력해주세요.")
    @field:Pattern(regexp = "^01[016789]\\d{7,8}$", message = "올바른 핸드폰 번호를 입력해주세요. (예: 01012345678)")
    val phoneNumber: String,

    @Schema(description = "어드민 회원 유형 (ADMIN_USER | ADMIN_MASTER | ADMIN_DEVELOPER)", example = "ADMIN_USER")
    @field:NotNull(message = "회원 유형을 선택해주세요.")
    val memberType: MemberType,
)
