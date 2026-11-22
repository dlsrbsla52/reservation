package com.media.bus.iam.auth.controller

import com.media.bus.common.exceptions.NoAuthenticationException
import com.media.bus.common.result.type.CommonResult
import com.media.bus.common.web.response.ApiResponse
import com.media.bus.contract.security.JwtProvider
import com.media.bus.contract.security.MemberPrincipal
import com.media.bus.contract.security.TokenAudience
import com.media.bus.contract.security.annotation.CurrentMember
import com.media.bus.iam.auth.dto.*
import com.media.bus.iam.auth.service.AuthService
import com.media.bus.iam.auth.support.AuthCookieSupport
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*

/** 인증 API 컨트롤러 */
@Tag(name = "인증(IAM) API", description = "회원가입, 로그인, 로그아웃 등 인증 관련 API")
@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val authService: AuthService,
    private val jwtProvider: JwtProvider,
    private val cookieSupport: AuthCookieSupport,
) {
    companion object {
        private const val REFRESH_TOKEN_COOKIE = AuthCookieSupport.USER_REFRESH_COOKIE
    }

    /** 회원가입. 이메일 인증 토큰은 보안상 응답에 포함하지 않으며 이메일로만 발송된다. */
    @Operation(summary = "회원가입", description = "새로운 회원을 가입시킵니다. 이메일 인증 안내 메일이 발송됩니다.")
    @PostMapping("/register")
    fun register(@RequestBody @Valid request: RegisterRequest): ApiResponse<Unit?> {
        authService.register(request)
        return ApiResponse.successWithMessage("회원가입이 완료되었습니다. 이메일 인증을 진행해주세요.")
    }

    /** 로그인. */
    @Operation(summary = "로그인", description = "아이디와 비밀번호로 로그인합니다. Access Token은 응답 바디, Refresh Token은 HttpOnly Cookie로 전달됩니다.")
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
        )
        cookieSupport.setRefreshTokenCookie(response, result.refreshToken, TokenAudience.USER)
        return ApiResponse.success(TokenResponse.of(result.accessToken))
    }

    /** 이메일 인증. */
    @Operation(summary = "이메일 인증", description = "가입 시 제공받은 인증 토큰으로 이메일을 인증합니다.")
    @GetMapping("/verify-email")
    fun verifyEmail(@RequestParam token: String): ApiResponse<Unit?> {
        authService.verifyEmail(token)
        return ApiResponse.successWithMessage("이메일 인증이 완료되었습니다.")
    }

    /** Access Token 재발급. */
    @Operation(summary = "토큰 재발급(Refresh)", description = "HttpOnly Cookie의 Refresh Token으로 새로운 Access Token과 Refresh Token을 발급받습니다.")
    @PostMapping("/token/refresh")
    fun refreshToken(
        @CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) refreshToken: String?,
        response: HttpServletResponse,
    ): ApiResponse<TokenResponse> {
        if (refreshToken == null) {
            throw NoAuthenticationException(CommonResult.ACCESS_TOKEN_EXPIRED_FAIL)
        }
        val result = authService.refreshAccessToken(refreshToken, TokenAudience.USER)
        cookieSupport.setRefreshTokenCookie(response, result.refreshToken, TokenAudience.USER)
        return ApiResponse.success(TokenResponse.of(result.accessToken))
    }

    @Operation(summary = "2차 본인 인증", description = "회원정보 수정, 비밀번호 변경, 탈퇴 등 민감한 작업 전 비밀번호로 본인을 재확인한다.")
    @PostMapping("/verify")
    fun verifyMember(
        @CurrentMember member: MemberPrincipal,
        @RequestBody @Valid request: VerifyMemberRequest,
    ): ApiResponse<Unit?> {
        authService.verifyMember(member.id.toString(), request)
        return ApiResponse.success()
    }

    /** 로그아웃. */
    @Operation(summary = "로그아웃", description = "서버에서 사용자의 Refresh Token을 삭제하고 Cookie를 만료시켜 로그아웃 처리합니다.")
    @PostMapping("/logout")
    fun logout(
        @CurrentMember member: MemberPrincipal,
        @CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) refreshToken: String?,
        response: HttpServletResponse,
    ): ApiResponse<Unit?> {
        val sessionId = refreshToken?.let { token ->
            jwtProvider.tryParseClaims(token)?.get(JwtProvider.SESSION_ID_CLAIM) as? String
        }
        authService.logout(member.id.toString(), sessionId)
        cookieSupport.expireRefreshTokenCookie(response, TokenAudience.USER)
        return ApiResponse.success()
    }

    /** 비밀번호 초기화 요청. loginId 또는 email로 초기화 토큰을 발급한다. */
    @Operation(summary = "비밀번호 초기화 요청", description = "로그인 아이디 또는 이메일로 비밀번호 초기화 토큰을 발급합니다.")
    @PostMapping("/password-reset/request")
    fun requestPasswordReset(@RequestBody @Valid request: PasswordResetRequest): ApiResponse<Unit?> {
        authService.requestPasswordReset(request)
        return ApiResponse.successWithMessage("비밀번호 초기화 안내가 발송되었습니다.")
    }

    /** 비밀번호 초기화 토큰 유효성 확인. */
    @Operation(summary = "비밀번호 초기화 토큰 검증", description = "비밀번호 초기화 토큰이 유효한지 확인합니다.")
    @PostMapping("/password-reset/verify")
    fun verifyPasswordResetToken(@RequestBody @Valid request: PasswordResetVerifyRequest): ApiResponse<Unit?> {
        authService.verifyPasswordResetToken(request.token)
        return ApiResponse.success()
    }

    /** 비밀번호 초기화 확정. 토큰과 새 비밀번호로 비밀번호를 변경한다. */
    @Operation(summary = "비밀번호 초기화 확정", description = "유효한 토큰과 새 비밀번호를 제출하여 비밀번호를 변경합니다.")
    @PostMapping("/password-reset/confirm")
    fun confirmPasswordReset(@RequestBody @Valid request: PasswordResetConfirmRequest): ApiResponse<Unit?> {
        authService.confirmPasswordReset(request)
        return ApiResponse.successWithMessage("비밀번호가 변경되었습니다. 새 비밀번호로 로그인해주세요.")
    }

    /** 로그인 상태에서 비밀번호 직접 변경. */
    @Operation(summary = "비밀번호 변경", description = "로그인된 사용자가 현재 비밀번호 확인 후 새 비밀번호로 변경합니다.")
    @PostMapping("/password/change")
    fun changePassword(
        @CurrentMember member: MemberPrincipal,
        @RequestBody @Valid request: PasswordChangeRequest,
    ): ApiResponse<Unit?> {
        authService.changePassword(member.id.toString(), request)
        return ApiResponse.successWithMessage("비밀번호가 변경되었습니다. 새 비밀번호로 다시 로그인해주세요.")
    }

    /** 비활성화(INACTIVE) 계정 재활성화. */
    @Operation(summary = "계정 재활성화", description = "사용자가 본인이 비활성화한 계정을 다시 활성화합니다.")
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
        )
        cookieSupport.setRefreshTokenCookie(response, result.refreshToken, TokenAudience.USER)
        return ApiResponse.success(TokenResponse.of(result.accessToken))
    }

    /** 내 활성 세션 목록을 조회한다. 현재 요청 세션은 `current=true`로 표시된다. */
    @Operation(summary = "내 세션 목록", description = "로그인된 사용자의 활성 디바이스 세션 목록을 반환합니다.")
    @GetMapping("/sessions")
    fun listSessions(
        @CurrentMember member: MemberPrincipal,
        @CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) refreshToken: String?,
    ): ApiResponse<List<SessionResponse>> {
        val currentSessionId = refreshToken?.let { jwtProvider.tryParseClaims(it)?.get(JwtProvider.SESSION_ID_CLAIM) as? String }
        return ApiResponse.success(authService.listMySessions(member.id.toString(), currentSessionId))
    }

    /** 특정 세션을 로그아웃 시킨다. */
    @Operation(summary = "특정 세션 로그아웃", description = "지정된 세션만 강제 로그아웃합니다. 다른 디바이스는 유지됩니다.")
    @DeleteMapping("/sessions/{sessionId}")
    fun revokeSession(
        @CurrentMember member: MemberPrincipal,
        @PathVariable sessionId: String,
    ): ApiResponse<Unit?> {
        authService.revokeSession(member.id.toString(), sessionId)
        return ApiResponse.successWithMessage("세션이 로그아웃되었습니다.")
    }

    /** 현재 요청 세션을 제외한 모든 세션을 로그아웃 시킨다. */
    @Operation(summary = "다른 기기 모두 로그아웃", description = "현재 세션을 제외한 모든 디바이스를 강제 로그아웃합니다.")
    @DeleteMapping("/sessions")
    fun revokeOtherSessions(
        @CurrentMember member: MemberPrincipal,
        @CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) refreshToken: String?,
    ): ApiResponse<Unit?> {
        val currentSessionId = refreshToken?.let { jwtProvider.tryParseClaims(it)?.get(JwtProvider.SESSION_ID_CLAIM) as? String }
            ?: throw NoAuthenticationException(CommonResult.ACCESS_TOKEN_EXPIRED_FAIL)
        authService.revokeOtherSessions(member.id.toString(), currentSessionId)
        return ApiResponse.successWithMessage("다른 디바이스 세션이 모두 로그아웃되었습니다.")
    }
}
