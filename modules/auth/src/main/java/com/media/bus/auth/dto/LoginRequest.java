package com.media.bus.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 로그인 요청 DTO.
 */
@Schema(description = "로그인 요청 DTO")
public record LoginRequest(

    @Schema(description = "로그인 아이디", example = "user123")
    @NotBlank(message = "아이디를 입력해주세요.")
    String loginId,

    @Schema(description = "비밀번호", example = "Password123!")
    @NotBlank(message = "비밀번호를 입력해주세요.")
    String password

) {
}
