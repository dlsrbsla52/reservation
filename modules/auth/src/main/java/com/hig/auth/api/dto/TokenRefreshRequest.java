package com.hig.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Access Token 재발급 요청 DTO.
 */
@Schema(description = "Access Token 재발급 요청 DTO")
public record TokenRefreshRequest(
        @Schema(description = "발급받은 Refresh Token", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...") @NotBlank(message = "Refresh Token을 입력해주세요.") String refreshToken) {
}
