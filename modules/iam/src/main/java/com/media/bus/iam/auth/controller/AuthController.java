package com.media.bus.iam.auth.controller;

import com.media.bus.common.exceptions.NoAuthenticationException;
import com.media.bus.common.result.type.CommonResult;
import com.media.bus.common.web.response.ApiResponse;
import com.media.bus.iam.auth.dto.AuthTokenResult;
import com.media.bus.iam.auth.dto.LoginRequest;
import com.media.bus.iam.auth.dto.RegisterRequest;
import com.media.bus.iam.auth.dto.TokenResponse;
import com.media.bus.iam.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/// 인증 API 컨트롤러.
/// 모든 엔드포인트는 Gateway의 JWT 검증 화이트리스트에 포함되어야 합니다.
/// (인증 없이 접근 가능한 Public Endpoints)
///
/// **토큰 전달 전략**
/// - Access Token  → Response Body (`accessToken` 필드)
/// - Refresh Token → HttpOnly Cookie (`refresh_token`) — JS 접근 불가, XSS 방어
@Tag(name = "인증(IAM) API", description = "회원가입, 로그인, 로그아웃 등 인증 관련 API")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    /// Refresh Token Cookie 이름
    private static final String REFRESH_TOKEN_COOKIE = "refresh_token";

    /// Refresh Token TTL — JwtProvider의 Refresh Token TTL과 반드시 동일해야 합니다.
    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(7);

    private final AuthService authService;

    /// `cookie.secure` 설정값.
    /// 운영 환경(HTTPS)에서는 true, 로컬 개발(HTTP)에서는 false로 설정합니다.
    @Value("${cookie.secure:false}")
    private boolean cookieSecure;

    /// 회원가입.
    /// 이메일 인증 토큰은 보안상 응답에 포함하지 않으며 이메일로만 발송됩니다.
    @Operation(summary = "회원가입", description = "새로운 회원을 가입시킵니다. 이메일 인증 안내 메일이 발송됩니다.")
    @PostMapping("/register")
    public ApiResponse<Void> register(@RequestBody @Valid RegisterRequest request) {
        authService.register(request);
        return ApiResponse.successWithMessage("회원가입이 완료되었습니다. 이메일 인증을 진행해주세요.");
    }

    /// 로그인.
    /// Access Token은 Response Body로, Refresh Token은 HttpOnly Cookie(`refresh_token`)로 반환합니다.
    @Operation(summary = "로그인", description = "아이디와 비밀번호로 로그인합니다. Access Token은 응답 바디, Refresh Token은 HttpOnly Cookie로 전달됩니다.")
    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(
            @RequestBody @Valid LoginRequest request,
            HttpServletResponse response) {
        AuthTokenResult result = authService.login(request);
        setRefreshTokenCookie(response, result.refreshToken());
        return ApiResponse.success(TokenResponse.of(result.accessToken()));
    }

    /// 이메일 인증.
    /// 이메일로 발송된 인증 링크의 token 파라미터를 검증합니다.
    /// 예: GET /api/v1/auth/verify-email?token={uuid}
    @Operation(summary = "이메일 인증", description = "가입 시 제공받은 인증 토큰으로 이메일을 인증합니다.")
    @GetMapping("/verify-email")
    public ApiResponse<Void> verifyEmail(@RequestParam String token) {
        authService.verifyEmail(token);
        return ApiResponse.successWithMessage("이메일 인증이 완료되었습니다.");
    }

    /// Access Token 재발급.
    /// HttpOnly Cookie(`refresh_token`)에서 Refresh Token을 읽어 새로운 Access Token과 Refresh Token을 발급합니다 (Token Rotation).
    /// 쿠키가 없거나 만료된 경우 401을 반환합니다.
    @Operation(summary = "토큰 재발급(Refresh)", description = "HttpOnly Cookie의 Refresh Token으로 새로운 Access Token과 Refresh Token을 발급받습니다.")
    @PostMapping("/token/refresh")
    public ApiResponse<TokenResponse> refreshToken(
            @CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) String refreshToken,
            HttpServletResponse response) {
        if (refreshToken == null) {
            throw new NoAuthenticationException(CommonResult.ACCESS_TOKEN_EXPIRED_FAIL);
        }
        AuthTokenResult result = authService.refreshAccessToken(refreshToken);
        setRefreshTokenCookie(response, result.refreshToken());
        return ApiResponse.success(TokenResponse.of(result.accessToken()));
    }

    /// 로그아웃.
    /// Gateway에서 주입한 X-User-Id 헤더를 기반으로 Redis의 Refresh Token을 삭제하고 Cookie를 만료시킵니다.
    /// 이 엔드포인트는 JWT 인증이 필요한 Protected Endpoint입니다.
    @Operation(summary = "로그아웃", description = "서버에서 사용자의 Refresh Token을 삭제하고 Cookie를 만료시켜 로그아웃 처리합니다.")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @RequestHeader("X-User-Id") String memberId,
            HttpServletResponse response) {
        authService.logout(memberId);
        expireRefreshTokenCookie(response);
        return ApiResponse.success();
    }

    /// Refresh Token을 HttpOnly Cookie로 설정합니다.
    private void setRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE, refreshToken)
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/api/v1/auth")   // /login, /token/refresh, /logout 범위로 제한
                .maxAge(REFRESH_TOKEN_TTL)
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /// Refresh Token Cookie를 즉시 만료시킵니다 (로그아웃 시 사용).
    private void expireRefreshTokenCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/api/v1/auth")
                .maxAge(Duration.ZERO)
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}