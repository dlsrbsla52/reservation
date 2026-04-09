package com.media.bus.iam.member.entity

import com.media.bus.common.entity.common.DateBaseTable
import com.media.bus.iam.member.entity.enumerated.MemberStatus

/**
 * ## 회원 인증 정보 테이블 정의
 *
 * 스키마: `auth`, 테이블: `member`
 * 인증/인가에 필요한 최소 정보만 관리한다.
 */
object MemberTable : DateBaseTable("auth.member") {
    val loginId = varchar("login_id", 50).uniqueIndex("uq_member_login_id")
    val password = varchar("password", 255)
    val email = varchar("email", 100).uniqueIndex("uq_member_email")
    val phoneNumber = varchar("phone_number", 20)
    val emailVerified = bool("email_verified").default(false)
    val status = enumerationByName<MemberStatus>("status", 20).default(MemberStatus.ACTIVE)

    /** 비즈니스 회원 전용. 일반 회원(MEMBER)의 경우 null. */
    val businessNumber = varchar("business_number", 20).nullable()
}
