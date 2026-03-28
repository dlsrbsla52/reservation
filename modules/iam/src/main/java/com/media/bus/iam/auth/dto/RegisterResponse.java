package com.media.bus.iam.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/// 회원가입 응답 DTO.
/// 보안상 이메일 인증 토큰은 응답에 포함하지 않습니다.
/// 성공 여부는 HTTP 200 + ApiResponse로 표현합니다.
@Schema(description = "회원가입 응답 DTO")
public record RegisterResponse() {
}
