package com.hig.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "회원가입 응답 DTO")
public record RegisterResponse(
        @Schema(description = "이메일 인증용 토큰", example = "123e4567-e89b-12d3-a456-426614174000")
        String emailVerifyToken
) {
}
