package com.media.bus.iam.auth.entity

import com.media.bus.common.entity.common.DateBaseTable

/**
 * ## 역할 마스터 테이블 정의
 *
 * 스키마: `auth`, 테이블: `role`
 * `MemberType` enum의 name() 값을 DB 레코드로 관리한다.
 */
object RoleTable : DateBaseTable("auth.role") {
    /** MemberType.name() 값 (예: "MEMBER", "ADMIN_USER"). UNIQUE 제약 보장. */
    val name = varchar("name", 30).uniqueIndex()
    val displayName = varchar("display_name", 100)
}
