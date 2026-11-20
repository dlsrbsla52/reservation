package com.media.bus.iam.admin.dto

import com.media.bus.contract.entity.member.MemberType
import com.media.bus.iam.member.entity.MemberEntity
import com.media.bus.iam.member.entity.enumerated.MemberStatus
import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime
import java.util.*

/** 회원 조건 검색 결과 행 DTO */
@Schema(description = "회원 조건 검색 결과 응답 DTO")
data class MemberSearchResponse(

    @param:Schema(description = "회원 ID")
    val memberId: UUID,

    @param:Schema(description = "로그인 아이디")
    val loginId: String,

    @param:Schema(description = "이메일")
    val email: String,

    @param:Schema(description = "회원 이름")
    val memberName: String,

    @param:Schema(description = "전화번호")
    val phoneNumber: String,

    @param:Schema(description = "사업자번호(비즈니스 회원만, 일반 회원은 null)")
    val businessNumber: String?,

    @param:Schema(description = "회원 유형")
    val memberType: MemberType,

    @param:Schema(description = "계정 상태")
    val status: MemberStatus,

    @param:Schema(description = "가입일시")
    val createdAt: OffsetDateTime,
) {
    companion object {
        fun of(member: MemberEntity, memberType: MemberType): MemberSearchResponse =
            MemberSearchResponse(
                memberId = member.id.value,
                loginId = member.loginId,
                email = member.email,
                memberName = member.memberName,
                phoneNumber = member.phoneNumber,
                businessNumber = member.businessNumber,
                memberType = memberType,
                status = member.status,
                createdAt = member.createdAt,
            )
    }
}
