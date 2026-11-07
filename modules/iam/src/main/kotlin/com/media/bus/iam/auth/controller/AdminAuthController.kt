package com.media.bus.iam.auth.controller

import com.media.bus.common.exceptions.NoAuthenticationException
import com.media.bus.common.result.type.CommonResult
import com.media.bus.common.web.response.ApiResponse
import com.media.bus.contract.security.JwtProvider
import com.media.bus.contract.security.TokenAudience
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

/**
 * ## 관리자 사이트 전용 인증 API
 *
 * 일반 사이트 인증(`/api/v1/auth`)과 분리한 이유:
 * - 발급되는 토큰의 `aud`가 `ADMIN`이어야 gateway가 `/api/v1/admin` 하위 경로 진입을 허용한다.
 * - Refresh Token 쿠키(`admin_refresh_token`, Path `/api/v1/auth/admin`)를 일반 사이트와 나눠
 *   일반 화면에서 관리자 세션을 갱신할 수 없게 한다.
 *
 * 경로를 `/api/v1/auth/admin` 하위에 둔 이유: gateway 의 iam 라우트(`/api/v1/auth` 하위)를 그대로 타고,
 * gateway CORS 가 이 하위 경로를 관리자 origin 만 허용하도록 묶기 쉽다.
 */
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

    /**
     * 비활성화(INACTIVE)된 관리자 계정 재활성화.
     * 관리자 로그인에서 `ACCOUNT_INACTIVE_FAIL`을 받은 관리자가 복귀하는 경로다.
     * 일반 재활성화(`/api/v1/auth/reactivate`)는 관리자 계정을 받지 않으므로 별도로 둔다.
     */
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
        @RequestHeader("X-User-Id") memberId: String,
        @CookieValue(name = AuthCookieSupport.ADMIN_REFRESH_COOKIE, required = false) refreshToken: String?,
        response: HttpServletResponse,
    ): ApiResponse<Unit?> {
        val sessionId = refreshToken?.let { token ->
            jwtProvider.tryParseClaims(token)?.get(JwtProvider.SESSION_ID_CLAIM) as? String
        }
        authService.logout(memberId, sessionId)
        cookieSupport.expireRefreshTokenCookie(response, TokenAudience.ADMIN)
        return ApiResponse.success()
    }
}
