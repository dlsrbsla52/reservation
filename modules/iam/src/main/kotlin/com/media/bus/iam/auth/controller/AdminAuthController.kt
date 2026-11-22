package com.media.bus.iam.auth.controller

import com.media.bus.common.exceptions.NoAuthenticationException
import com.media.bus.common.result.type.CommonResult
import com.media.bus.common.web.response.ApiResponse
import com.media.bus.contract.security.JwtProvider
import com.media.bus.contract.security.MemberPrincipal
import com.media.bus.contract.security.TokenAudience
import com.media.bus.contract.security.annotation.CurrentMember
import com.media.bus.iam.auth.dto.LoginRequest
import com.media.bus.iam.auth.dto.TokenResponse
import com.media.bus.iam.auth.service.AuthService
import com.media.bus.iam.auth.support.AuthCookieSupport
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*

/** 관리자 사이트 전용 인증 API */
@Tag(name = "관리자 인증 API", description = "관리자 사이트 로그인·토큰 갱신·로그아웃")
@RestController
@RequestMapping("/api/v1/auth/admin")
class AdminAuthController(
    private val authService: AuthService,
    private val jwtProvider: JwtProvider,
    private val cookieSupport: AuthCookieSupport,
) {

    /** 관리자 로그인. 관리자 계정이 아니면 403. 액세스 토큰 TTL은 15분이다. */
    @Operation(summary = "관리자 로그인", description = "관리자 계정만 로그인할 수 있습니다. Refresh Token은 관리자 전용 HttpOnly Cookie로 전달됩니다.")
    @PostMapping("/login")
    fun login(
        @RequestBody @Valid request: LoginRequest,
        httpRequest: HttpServletRequest,
        response: HttpServletResponse,
    ): ApiResponse<TokenResponse> {
        val result = authService.login(
            request = request,
            deviceInfo = cookieSupport.extractDeviceInfo(httpRequest),
            ip = cookieSupport.extractClientIp(httpRequest),
            audience = TokenAudience.ADMIN,
        )
        cookieSupport.setRefreshTokenCookie(response, result.refreshToken, TokenAudience.ADMIN)
        return ApiResponse.success(TokenResponse.of(result.accessToken))
    }

    /** 관리자 토큰 재발급. 관리자 쿠키의 Refresh Token만 받으며 발급처가 `ADMIN`이 아니면 401. */
    @Operation(summary = "관리자 토큰 재발급", description = "관리자 전용 Refresh Token Cookie로 새 토큰을 발급합니다.")
    @PostMapping("/token/refresh")
    fun refreshToken(
        @CookieValue(name = AuthCookieSupport.ADMIN_REFRESH_COOKIE, required = false) refreshToken: String?,
        response: HttpServletResponse,
    ): ApiResponse<TokenResponse> {
        if (refreshToken == null) {
            throw NoAuthenticationException(CommonResult.ACCESS_TOKEN_EXPIRED_FAIL)
        }
        val result = authService.refreshAccessToken(refreshToken, TokenAudience.ADMIN)
        cookieSupport.setRefreshTokenCookie(response, result.refreshToken, TokenAudience.ADMIN)
        return ApiResponse.success(TokenResponse.of(result.accessToken))
    }

    /** 비활성화(INACTIVE)된 관리자 계정 재활성화. */
    @Operation(summary = "관리자 계정 재활성화", description = "본인이 비활성화한 관리자 계정을 다시 활성화하고 관리자 토큰을 발급합니다.")
    @PostMapping("/reactivate")
    fun reactivate(
        @RequestBody @Valid request: LoginRequest,
        httpRequest: HttpServletRequest,
        response: HttpServletResponse,
    ): ApiResponse<TokenResponse> {
        val result = authService.reactivate(
            request = request,
            deviceInfo = cookieSupport.extractDeviceInfo(httpRequest),
            ip = cookieSupport.extractClientIp(httpRequest),
            audience = TokenAudience.ADMIN,
        )
        cookieSupport.setRefreshTokenCookie(response, result.refreshToken, TokenAudience.ADMIN)
        return ApiResponse.success(TokenResponse.of(result.accessToken))
    }

    /** 관리자 로그아웃. 현재 관리자 세션만 삭제하고 관리자 쿠키를 만료시킨다. */
    @Operation(summary = "관리자 로그아웃", description = "현재 관리자 세션의 Refresh Token을 삭제하고 Cookie를 만료시킵니다.")
    @PostMapping("/logout")
    fun logout(
        @CurrentMember member: MemberPrincipal,
        @CookieValue(name = AuthCookieSupport.ADMIN_REFRESH_COOKIE, required = false) refreshToken: String?,
        response: HttpServletResponse,
    ): ApiResponse<Unit?> {
        val sessionId = refreshToken?.let { token ->
            jwtProvider.tryParseClaims(token)?.get(JwtProvider.SESSION_ID_CLAIM) as? String
        }
        authService.logout(member.id.toString(), sessionId)
        cookieSupport.expireRefreshTokenCookie(response, TokenAudience.ADMIN)
        return ApiResponse.success()
    }
}
