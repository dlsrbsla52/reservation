package com.media.bus.iam.member.entity

import com.media.bus.common.entity.common.DateBaseTable
import com.media.bus.iam.member.entity.enumerated.MemberStatus

/** 회원 인증 정보 테이블 정의 */
object MemberTable : DateBaseTable("auth.member") {
    val loginId = varchar("login_id", 50).uniqueIndex("uq_member_login_id")
    val password = varchar("password", 255)
    val email = varchar("email", 100).uniqueIndex("uq_member_email")
    val phoneNumber = varchar("phone_number", 20)
    val emailVerified = bool("email_verified").default(false)
    val status = enumerationByName<MemberStatus>("status", 20).default(MemberStatus.ACTIVE)
    val memberName = varchar("member_name", 10)

    val businessNumber = varchar("business_number", 20).nullable()
}
