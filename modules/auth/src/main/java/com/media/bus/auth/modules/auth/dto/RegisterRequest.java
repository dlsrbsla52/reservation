package com.media.bus.auth.modules.auth.dto;

import com.media.bus.contract.entity.member.MemberType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;

/**
 * 회원가입 요청 DTO.
 * - 일반 회원(MEMBER): businessNumber 필드 불필요.
 * - 비즈니스 회원(BUSINESS): businessNumber 필수.
 * 비즈니스 규칙에 따른 Cross-Field 검증은 AuthService 계층에서 수행합니다.
 */
@Schema(description = "회원가입 요청 DTO")
@Builder
public record RegisterRequest(

    @Schema(description = "로그인 아이디 (4자 이상 50자 이하, 영문/숫자/언더스코어만 허용)", example = "user123")
    @NotBlank(message = "아이디를 입력해주세요.")
    @Size(min = 4, max = 50, message = "아이디는 4자 이상 50자 이하로 입력해주세요.")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "아이디는 영문, 숫자, 언더스코어(_)만 사용 가능합니다.")
    String loginId,

    @Schema(description = "비밀번호 (영문, 숫자, 특수문자를 각각 1개 이상 포함한 8자 이상)", example = "Password123!")
    @NotBlank(message = "비밀번호를 입력해주세요.")
    @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*#?&])[A-Za-z\\d@$!%*#?&]+$", message = "비밀번호는 영문, 숫자, 특수문자를 각각 1개 이상 포함해야 합니다.")
    String password,

    @Schema(description = "사용자 이메일 주소", example = "user@example.com")
    @NotBlank(message = "이메일을 입력해주세요.")
    @Email(message = "올바른 이메일 형식을 입력해주세요.")
    String email,

    @Schema(description = "핸드폰 번호 (하이픈 제외, 숫자만)", example = "01012345678")
    @NotBlank(message = "핸드폰 번호를 입력해주세요.")
    @Pattern(regexp = "^01[016789]\\d{7,8}$", message = "올바른 핸드폰 번호를 입력해주세요. (예: 01012345678)")
    String phoneNumber,

    @Schema(description = "회원 유형 (MEMBER 또는 BUSINESS)", example = "MEMBER")
    @NotNull(message = "회원 유형을 선택해주세요.")
    MemberType memberType,

    @Schema(description = "비즈니스 회원 전용 사업자등록번호", example = "123-45-67890", nullable = true)
    // 비즈니스 회원 전용. 일반 회원은 null 허용.
    String businessNumber

) {
}
