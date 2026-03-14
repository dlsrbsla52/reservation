package com.hig.auth.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * JWT 발급 응답 DTO.
 */
@Schema(description = "인증 토큰 응답 DTO")
public record TokenResponse(
    @Schema(description = "Access Token", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    String accessToken,

    @Schema(description = "Refresh Token", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    String refreshToken,

    @Schema(description = "토큰 타입", example = "Bearer")
    String tokenType
) {
    /** 표준 Bearer 방식을 기본 토큰 타입으로 사용합니다. */
    public static TokenResponse of(String accessToken, String refreshToken) {
        return new TokenResponse(accessToken, refreshToken, "Bearer");
    }
}
