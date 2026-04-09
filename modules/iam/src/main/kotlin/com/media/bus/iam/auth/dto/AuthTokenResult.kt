package com.media.bus.iam.auth.dto

/**
 * 서비스 계층에서 컨트롤러로 Access Token + Refresh Token 쌍을 전달하기 위한 내부 DTO.
 * HTTP 응답에 직접 노출되지 않으며, 컨트롤러가 각각 Body와 Cookie로 분리하여 처리한다.
 */
data class AuthTokenResult(val accessToken: String, val refreshToken: String)
