package com.media.bus.iam.auth.entity

import com.media.bus.common.entity.common.DateBaseTable
import com.media.bus.iam.member.entity.MemberTable

/**
 * ## 회원-역할 매핑 테이블 정의
 *
 * 스키마: `auth`, 테이블: `member_role`
 * `member_id`에 UNIQUE 제약을 적용하여 한 회원이 하나의 역할만 가지도록 보장한다.
 */
object MemberRoleTable : DateBaseTable("auth.member_role") {
    /** 역할을 소유한 회원 FK. 한 회원은 하나의 역할만 가질 수 있다. */
    val memberId = reference("member_id", MemberTable).uniqueIndex("uq_member_role_single")
    val roleId = reference("role_id", RoleTable)
}
