package com.media.bus.iam.auth.dto

import io.swagger.v3.oas.annotations.media.Schema

/**
 * JWT 발급 응답 DTO.
 * Refresh Token은 HttpOnly Cookie로 전달되므로 응답 바디에 포함하지 않는다.
 */
@Schema(description = "인증 토큰 응답 DTO")
data class TokenResponse(
    @param:Schema(description = "Access Token", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    val accessToken: String,

    @param:Schema(description = "토큰 타입", example = "Bearer")
    val tokenType: String,
) {
    companion object {
        /** 표준 Bearer 방식을 기본 토큰 타입으로 사용한다. */
        fun of(accessToken: String): TokenResponse = TokenResponse(accessToken, "Bearer")
    }
}
