package com.media.bus.iam.auth.entity

import com.media.bus.common.entity.common.DateBaseTable
import com.media.bus.iam.member.entity.MemberTable

/** 회원-역할 매핑 테이블 정의 */
object MemberRoleTable : DateBaseTable("auth.member_role") {
    val memberId = reference("member_id", MemberTable).uniqueIndex("uq_member_role_single")
    val roleId = reference("role_id", RoleTable)
}
