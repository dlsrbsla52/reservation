package com.media.bus.iam.member.dto

import com.media.bus.iam.member.entity.MemberEntity

/** Repository 조회 결과로 회원 엔티티와 역할 이름을 함께 전달하는 내부 전송 객체 */
data class MemberWithRole(
    val member: MemberEntity,
    val roleName: String,
)
