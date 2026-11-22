package com.media.bus.iam.auth.dto

/** 서비스 계층에서 컨트롤러로 Access Token + Refresh Token 쌍을 전달하기 위한 내부 DTO. */
data class AuthTokenResult(val accessToken: String, val refreshToken: String)
