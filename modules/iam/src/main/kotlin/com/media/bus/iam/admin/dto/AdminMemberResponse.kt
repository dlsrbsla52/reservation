package com.media.bus.iam.admin.dto

import com.media.bus.contract.entity.member.MemberType
import com.media.bus.iam.member.entity.MemberEntity
import io.swagger.v3.oas.annotations.media.Schema
import java.util.*

/** 어드민 멤버 생성 응답 DTO */
@Schema(description = "어드민 멤버 생성 응답 DTO")
data class AdminMemberResponse(

    @Schema(description = "생성된 멤버 ID")
    val memberId: UUID,

    @Schema(description = "로그인 아이디")
    val loginId: String,

    @Schema(description = "이메일 주소")
    val email: String,

    @Schema(description = "부여된 어드민 회원 유형")
    val memberType: MemberType,
) {
    companion object {
        /**
         * Member 엔티티와 MemberType으로 응답 DTO를 생성한다.
         * MemberType은 엔티티에 없으므로 Role 조회 결과를 별도 전달한다.
         */
        fun of(member: MemberEntity, memberType: MemberType): AdminMemberResponse =
            AdminMemberResponse(
                memberId = member.id.value,
                loginId = member.loginId,
                email = member.email,
                memberType = memberType,
            )
    }
}
