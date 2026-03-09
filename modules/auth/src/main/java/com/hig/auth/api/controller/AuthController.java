package com.hig.auth.api.controller;

import com.hig.auth.api.dto.LoginRequest;
import com.hig.auth.api.dto.RegisterRequest;
import com.hig.auth.api.dto.RegisterResponse;
import com.hig.auth.api.dto.TokenRefreshRequest;
import com.hig.auth.api.dto.TokenResponse;
import com.hig.auth.service.AuthService;
import com.hig.mvc.response.DataView;
import com.hig.mvc.response.NoDataView;
import com.hig.types.CommonResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 API 컨트롤러.
 * 모든 엔드포인트는 Gateway의 JWT 검증 화이트리스트에 포함되어야 합니다.
 * (인증 없이 접근 가능한 Public Endpoints)
 */
@Tag(name = "인증(Auth) API", description = "회원가입, 로그인, 로그아웃 등 인증 관련 API")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 회원가입.
     * 성공 시 이메일 인증 토큰 반환 (개발 단계: 실제 서비스에서는 이메일로 발송).
     */
    @Operation(summary = "회원가입", description = "새로운 회원을 가입시킵니다. 성공 시 이메일 인증 토큰을 반환합니다.")
    @PostMapping("/register")
    public DataView<RegisterResponse> register(@RequestBody @Valid RegisterRequest request) {
        String emailVerifyToken = authService.register(request);
        return DataView.<RegisterResponse>builder()
                .result(CommonResult.SUCCESS)
                .message("회원가입이 완료되었습니다. 이메일 인증을 진행해주세요.")
                // TODO: 실제 서비스에서는 이 토큰을 응답 바디에서 제거하고 이메일로만 발송
                .data(new RegisterResponse(emailVerifyToken))
                .build();
    }

    /**
     * 로그인.
     * Access Token + Refresh Token을 발급하여 반환합니다.
     */
    @Operation(summary = "로그인", description = "아이디와 비밀번호로 로그인하여 Access Token과 Refresh Token을 발급받습니다.")
    @PostMapping("/login")
    public DataView<TokenResponse> login(@RequestBody @Valid LoginRequest request) {
        return DataView.<TokenResponse>builder()
                .result(CommonResult.SUCCESS)
                .data(authService.login(request))
                .build();
    }

    /**
     * 이메일 인증.
     * 이메일로 발송된 인증 링크의 token 파라미터를 검증합니다.
     * 예: GET /api/v1/auth/verify-email?token={uuid}
     */
    @Operation(summary = "이메일 인증", description = "가입 시 제공받은 인증 토큰으로 이메일을 인증합니다.")
    @GetMapping("/verify-email")
    public NoDataView verifyEmail(@RequestParam String token) {
        authService.verifyEmail(token);
        // 데이터가 없는 경우는 ResponseAdvisor가 null을 DataView로 변환하므로
        // 명시적으로 NoDataView를 반환합니다.
        return NoDataView.builder()
                .result(CommonResult.SUCCESS)
                .message("이메일 인증이 완료되었습니다.")
                .build();
    }

    /**
     * Access Token 재발급.
     * Refresh Token으로 새로운 Access Token과 Refresh Token을 발급합니다 (Token Rotation).
     */
    @Operation(summary = "토큰 재발급(Refresh)", description = "Refresh Token을 사용하여 새로운 Access Token과 Refresh Token을 발급받습니다.")
    @PostMapping("/token/refresh")
    public DataView<TokenResponse> refreshToken(@RequestBody @Valid TokenRefreshRequest request) {
        return DataView.<TokenResponse>builder()
                .result(CommonResult.SUCCESS)
                .data(authService.refreshAccessToken(request.refreshToken()))
                .build();
    }

    /**
     * 로그아웃.
     * Gateway에서 주입한 X-User-Id 헤더를 기반으로 Redis의 Refresh Token을 삭제합니다.
     * 이 엔드포인트는 JWT 인증이 필요한 Protected Endpoint입니다.
     */
    @Operation(summary = "로그아웃", description = "서버에서 사용자의 Refresh Token을 삭제하여 로그아웃 처리합니다.")
    @PostMapping("/logout")
    public NoDataView logout(@RequestHeader("X-User-Id") String userId) {
        authService.logout(userId);
        // 데이터가 없는 경우는 명시적으로 NoDataView를 반환합니다.
        return NoDataView.builder()
                .result(CommonResult.SUCCESS)
                .build();
    }
}
