package com.media.bus.iam.auth.controller

import com.media.bus.common.exceptions.NoAuthenticationException
import com.media.bus.common.result.type.CommonResult
import com.media.bus.common.web.response.ApiResponse
import com.media.bus.contract.security.JwtProvider
import com.media.bus.iam.auth.dto.*
import com.media.bus.iam.auth.service.AuthService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.web.bind.annotation.*
import java.time.Duration

/**
 * ## 인증 API 컨트롤러
 *
 * 모든 엔드포인트는 Gateway의 JWT 검증 화이트리스트에 포함되어야 한다.
 *
 * **토큰 전달 전략**
 * - Access Token  → Response Body (`accessToken` 필드)
 * - Refresh Token → HttpOnly Cookie (`refresh_token`) — JS 접근 불가, XSS 방어
 */
@Tag(name = "인증(IAM) API", description = "회원가입, 로그인, 로그아웃 등 인증 관련 API")
@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val authService: AuthService,
    private val jwtProvider: JwtProvider,
    /** `cookie.secure` 설정값. 운영 환경(HTTPS)에서는 true, 로컬 개발(HTTP)에서는 false. */
    @param:Value($$"${cookie.secure:false}") private val cookieSecure: Boolean = false,
) {
    companion object {
        private const val REFRESH_TOKEN_COOKIE = "refresh_token"

        /** Refresh Token TTL — JwtProvider의 Refresh Token TTL과 반드시 동일해야 한다. */
        private val REFRESH_TOKEN_TTL = Duration.ofDays(7)
    }

    /** 회원가입. 이메일 인증 토큰은 보안상 응답에 포함하지 않으며 이메일로만 발송된다. */
    @Operation(summary = "회원가입", description = "새로운 회원을 가입시킵니다. 이메일 인증 안내 메일이 발송됩니다.")
    @PostMapping("/register")
    fun register(@RequestBody @Valid request: RegisterRequest): ApiResponse<Unit?> {
        authService.register(request)
        return ApiResponse.successWithMessage("회원가입이 완료되었습니다. 이메일 인증을 진행해주세요.")
    }

    /**
     * 로그인.
     * Access Token은 Response Body로, Refresh Token은 HttpOnly Cookie(`refresh_token`)로 반환한다.
     */
    @Operation(summary = "로그인", description = "아이디와 비밀번호로 로그인합니다. Access Token은 응답 바디, Refresh Token은 HttpOnly Cookie로 전달됩니다.")
    @PostMapping("/login")
    fun login(
        @RequestBody @Valid request: LoginRequest,
        httpRequest: HttpServletRequest,
        response: HttpServletResponse,
    ): ApiResponse<TokenResponse> {
        val result = authService.login(
            request = request,
            deviceInfo = extractDeviceInfo(httpRequest),
            ip = extractClientIp(httpRequest),
        )
        setRefreshTokenCookie(response, result.refreshToken)
        return ApiResponse.success(TokenResponse.of(result.accessToken))
    }

    /**
     * 이메일 인증.
     * 이메일로 발송된 인증 링크의 token 파라미터를 검증한다.
     * 예: `GET /api/v1/auth/verify-email?token={uuid}`
     */
    @Operation(summary = "이메일 인증", description = "가입 시 제공받은 인증 토큰으로 이메일을 인증합니다.")
    @GetMapping("/verify-email")
    fun verifyEmail(@RequestParam token: String): ApiResponse<Unit?> {
        authService.verifyEmail(token)
        return ApiResponse.successWithMessage("이메일 인증이 완료되었습니다.")
    }

    /**
     * Access Token 재발급.
     * HttpOnly Cookie(`refresh_token`)에서 Refresh Token을 읽어 새로운 토큰을 발급한다 (Token Rotation).
     * 쿠키가 없거나 만료된 경우 401을 반환한다.
     */
    @Operation(summary = "토큰 재발급(Refresh)", description = "HttpOnly Cookie의 Refresh Token으로 새로운 Access Token과 Refresh Token을 발급받습니다.")
    @PostMapping("/token/refresh")
    fun refreshToken(
        @CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) refreshToken: String?,
        response: HttpServletResponse,
    ): ApiResponse<TokenResponse> {
        if (refreshToken == null) {
            throw NoAuthenticationException(CommonResult.ACCESS_TOKEN_EXPIRED_FAIL)
        }
        val result = authService.refreshAccessToken(refreshToken)
        setRefreshTokenCookie(response, result.refreshToken)
        return ApiResponse.success(TokenResponse.of(result.accessToken))
    }


    @Operation(summary = "2차 본인 인증", description = "회원정보 수정, 비밀번호 변경, 탈퇴 등 민감한 작업 전 비밀번호로 본인을 재확인한다.")
    @PostMapping("/verify")
    fun verifyMember(
        @RequestHeader("X-User-Id") memberId: String,
        @RequestBody @Valid request: VerifyMemberRequest,
    ): ApiResponse<Unit?> {
        authService.verifyMember(memberId, request)
        return ApiResponse.success()
    }

    /**
     * 로그아웃.
     * Gateway에서 주입한 `X-User-Id` 헤더를 기반으로 Redis의 Refresh Token을 삭제하고 Cookie를 만료시킨다.
     */
    @Operation(summary = "로그아웃", description = "서버에서 사용자의 Refresh Token을 삭제하고 Cookie를 만료시켜 로그아웃 처리합니다.")
    @PostMapping("/logout")
    fun logout(
        @RequestHeader("X-User-Id") memberId: String,
        @CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) refreshToken: String?,
        response: HttpServletResponse,
    ): ApiResponse<Unit?> {
        // Refresh Cookie에서 sid claim을 추출하여 현재 세션만 로그아웃 시킨다 (타 디바이스 보존)
        val sessionId = refreshToken?.let { token ->
            jwtProvider.tryParseClaims(token)?.get(JwtProvider.SESSION_ID_CLAIM) as? String
        }
        authService.logout(memberId, sessionId)
        expireRefreshTokenCookie(response)
        return ApiResponse.success()
    }

    /** Refresh Token을 HttpOnly Cookie로 설정한다. */
    private fun setRefreshTokenCookie(response: HttpServletResponse, refreshToken: String) {
        val cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE, refreshToken)
            .httpOnly(true)
            .secure(cookieSecure)
            .path("/api/v1/auth")   // /login, /token/refresh, /logout 범위로 제한
            .maxAge(REFRESH_TOKEN_TTL)
            .sameSite("Lax")
            .build()
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString())
    }

    // ────────────────────────────────────────��────────────────────────
    // 비밀번호 초기화
    // ─────────────────────────────────────────────────────────────────

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

    /**
     * 로그인 상태에서 비밀번호 직접 변경.
     * 현재 비밀번호 검증으로 본인 확인을 갈음하며, 변경 후 모든 세션이 무효화된다.
     */
    @Operation(summary = "비밀번호 변경", description = "로그인된 사용자가 현재 비밀번호 확인 후 새 비밀번호로 변경합니다.")
    @PostMapping("/password/change")
    fun changePassword(
        @RequestHeader("X-User-Id") memberId: String,
        @RequestBody @Valid request: PasswordChangeRequest,
    ): ApiResponse<Unit?> {
        authService.changePassword(memberId, request)
        return ApiResponse.successWithMessage("비밀번호가 변경되었습니다. 새 비밀번호로 다시 로그인해주세요.")
    }

    /**
     * 비활성화(INACTIVE) 계정 재활성화.
     * 로그인 시도 시 `ACCOUNT_INACTIVE_FAIL`을 받은 사용자가 loginId/password로 복귀 요청한다.
     * 성공 시 Access Token + Refresh Token을 로그인과 동일하게 발급한다.
     */
    @Operation(summary = "계정 재활성화", description = "사용자가 본인이 비활성화한 계정을 다시 활성화합니다.")
    @PostMapping("/reactivate")
    fun reactivate(
        @RequestBody @Valid request: LoginRequest,
        httpRequest: HttpServletRequest,
        response: HttpServletResponse,
    ): ApiResponse<TokenResponse> {
        val result = authService.reactivate(
            request = request,
            deviceInfo = extractDeviceInfo(httpRequest),
            ip = extractClientIp(httpRequest),
        )
        setRefreshTokenCookie(response, result.refreshToken)
        return ApiResponse.success(TokenResponse.of(result.accessToken))
    }

    // ─────────────────────────────────────────────────────────────────
    // 세션 관리
    // ─────────────────────────────────────────────────────────────────

    /** 내 활성 세션 목록을 조회한다. 현재 요청 세션은 `current=true`로 표시된다. */
    @Operation(summary = "내 세션 목록", description = "로그인된 사용자의 활성 디바이스 세션 목록을 반환합니다.")
    @GetMapping("/sessions")
    fun listSessions(
        @RequestHeader("X-User-Id") memberId: String,
        @CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) refreshToken: String?,
    ): ApiResponse<List<SessionResponse>> {
        val currentSessionId = refreshToken?.let { jwtProvider.tryParseClaims(it)?.get(JwtProvider.SESSION_ID_CLAIM) as? String }
        return ApiResponse.success(authService.listMySessions(memberId, currentSessionId))
    }

    /** 특정 세션을 로그아웃 시킨다. */
    @Operation(summary = "특정 세션 로그아웃", description = "지정된 세션만 강제 로그아웃합니다. 다른 디바이스는 유지됩니다.")
    @DeleteMapping("/sessions/{sessionId}")
    fun revokeSession(
        @RequestHeader("X-User-Id") memberId: String,
        @PathVariable sessionId: String,
    ): ApiResponse<Unit?> {
        authService.revokeSession(memberId, sessionId)
        return ApiResponse.successWithMessage("세션이 로그아웃되었습니다.")
    }

    /** 현재 요청 세션을 제외한 모든 세션을 로그아웃 시킨다. */
    @Operation(summary = "다른 기기 모두 로그아웃", description = "현재 세션을 제외한 모든 디바이스를 강제 로그아웃합니다.")
    @DeleteMapping("/sessions")
    fun revokeOtherSessions(
        @RequestHeader("X-User-Id") memberId: String,
        @CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) refreshToken: String?,
    ): ApiResponse<Unit?> {
        val currentSessionId = refreshToken?.let { jwtProvider.tryParseClaims(it)?.get(JwtProvider.SESSION_ID_CLAIM) as? String }
            ?: throw NoAuthenticationException(CommonResult.ACCESS_TOKEN_EXPIRED_FAIL)
        authService.revokeOtherSessions(memberId, currentSessionId)
        return ApiResponse.successWithMessage("다른 디바이스 세션이 모두 로그아웃되었습니다.")
    }

    // ─────────────────────────────────────────────────────────────────
    // Request 메타데이터 추출 유틸
    // ─────────────────────────────────────────────────────────────────

    /** User-Agent 헤더를 기기 식별 정보로 사용한다. 없으면 null. */
    private fun extractDeviceInfo(request: HttpServletRequest): String? =
        request.getHeader("User-Agent")?.take(500)

    /**
     * 클라이언트 IP를 추출한다.
     * X-Forwarded-For 헤더(첫 번째 값) 우선 — Gateway/Load Balancer 뒤에서 실제 클라이언트 IP 보존.
     */
    private fun extractClientIp(request: HttpServletRequest): String? {
        val forwarded = request.getHeader("X-Forwarded-For")
        if (!forwarded.isNullOrBlank()) {
            return forwarded.substringBefore(",").trim().ifBlank { null }
        }
        return request.remoteAddr
    }

    // ────────────────────────────────────────────────────��────────────
    // Cookie 헬퍼
    // ─────────────────────────────────────────────────────────────────

    /** Refresh Token Cookie를 즉시 만료시킨다 (로그아웃 시 사용). */
    private fun expireRefreshTokenCookie(response: HttpServletResponse) {
        val cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE, "")
            .httpOnly(true)
            .secure(cookieSecure)
            .path("/api/v1/auth")
            .maxAge(Duration.ZERO)
            .sameSite("Lax")
            .build()
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString())
    }
}
