package com.media.bus.iam.admin.dto

import com.media.bus.contract.entity.member.MemberType
import com.media.bus.iam.member.entity.MemberEntity
import com.media.bus.iam.member.entity.enumerated.MemberStatus
import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime
import java.util.*

/** 어드민 회원 목록 조회용 간략 응답 DTO */
@Schema(description = "어드민 회원 목록 응답 DTO")
data class AdminMemberListResponse(

    @Schema(description = "회원 ID")
    val memberId: UUID,

    @Schema(description = "로그인 아이디")
    val loginId: String,

    @Schema(description = "이메일")
    val email: String,

    @Schema(description = "회원 이름")
    val memberName: String,

    @Schema(description = "회원 유형")
    val memberType: MemberType,

    @Schema(description = "계정 상태")
    val status: MemberStatus,

    @Schema(description = "생성일시")
    val createdAt: OffsetDateTime,
) {
    companion object {
        fun of(member: MemberEntity, memberType: MemberType): AdminMemberListResponse =
            AdminMemberListResponse(
                memberId = member.id.value,
                loginId = member.loginId,
                email = member.email,
                memberName = member.memberName,
                memberType = memberType,
                status = member.status,
                createdAt = member.createdAt,
            )
    }
}
