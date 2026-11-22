package com.media.bus.iam.auth.dto

import io.swagger.v3.oas.annotations.media.Schema

/** 활성 세션 응답 DTO */
@Schema(description = "활성 세션 정보")
data class SessionResponse(

    @param:Schema(description = "세션 식별자(UUID)", example = "550e8400-e29b-41d4-a716-446655440000")
    val sessionId: String,

    @param:Schema(description = "기기 정보 — 로그인 시점 User-Agent", nullable = true)
    val deviceInfo: String?,

    @param:Schema(description = "접속 IP", nullable = true)
    val ip: String?,

    @param:Schema(description = "세션 최초 발급 시각 (epoch millis)")
    val issuedAt: Long,

    @param:Schema(description = "마지막 Token Rotation 시각 (epoch millis)")
    val lastAccessedAt: Long,

    @param:Schema(description = "현재 요청을 보낸 세션 여부")
    val current: Boolean,
)
