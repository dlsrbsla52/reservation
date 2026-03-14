package com.hig.auth.member.dto;

import com.hig.entity.member.MemberStatus;
import com.hig.entity.member.MemberType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "회원 응답 DTO")
public record MemberResponse(

    @Schema(name = "id", description = "회원 ID", example = "123e4567-e89b-12d3-a456-4266141")
    UUID id,

    @Schema(name = "loginId", description = "로그인 아이디", example = "user123")
    String loginId,

    @Schema(name = "email", description = "이메일", example = "user@example.com")
    String email,

    @Schema(name = "phoneNumber", description = "휴대폰 전화번호", example = "010-1234-5678")
    String phoneNumber,

    @Schema(name = "memberType", description = "회원 유형 구분", example = "MEMBER")
    MemberType memberType,

    @Schema(name = "status", description = "회원 계정 상태", example = "ACTIVE")
    MemberStatus status,

    @Schema(name = "businessNumber", description = "기업회원 사업자 등로번호", example = "123-45-67890")
    String businessNumber,

    @Schema(name = "createdAt", description = "생성일자", example = "2026-03-13 13:19:10.389756 +00:00")
    OffsetDateTime createdAt,

    @Schema(name = "updatedAt", description = "수정일자", example = "2026-03-13 13:19:10.389756 +00:00")
    OffsetDateTime updatedAt
) {
}
