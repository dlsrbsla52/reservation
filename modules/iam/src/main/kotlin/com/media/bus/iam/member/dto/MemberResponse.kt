package com.media.bus.iam.member.dto

import com.media.bus.contract.entity.member.MemberType
import com.media.bus.iam.member.entity.enumerated.MemberStatus
import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime
import java.util.*

@Schema(description = "회원 응답 DTO")
data class MemberResponse(

    @Schema(name = "id", description = "회원 ID", example = "123e4567-e89b-12d3-a456-4266141")
    val id: UUID,

    @Schema(name = "loginId", description = "로그인 아이디", example = "user123")
    val loginId: String,

    @Schema(name = "email", description = "이메일", example = "user@example.com")
    val email: String,

    @Schema(name = "phoneNumber", description = "휴대폰 전화번호", example = "010-1234-5678")
    val phoneNumber: String,

    @Schema(name = "memberType", description = "회원 유형 구분", example = "MEMBER")
    val memberType: MemberType,

    @Schema(name = "status", description = "회원 계정 상태", example = "ACTIVE")
    val status: MemberStatus,

    @Schema(name = "businessNumber", description = "기업회원 사업자 등록번호", example = "123-45-67890")
    val businessNumber: String?,

    @Schema(name = "createdAt", description = "생성일자", example = "2026-03-13 13:19:10.389756 +00:00")
    val createdAt: OffsetDateTime,

    @Schema(name = "updatedAt", description = "수정일자", example = "2026-03-13 13:19:10.389756 +00:00")
    val updatedAt: OffsetDateTime,
)
