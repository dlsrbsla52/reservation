package com.media.bus.iam.auth.entity

import com.media.bus.common.entity.common.DateBaseTable

/**
 * ## 권한 마스터 테이블 정의
 *
 * 스키마: `auth`, 테이블: `permission`
 * `name`은 UNIQUE 제약이 있는 비즈니스 식별자다.
 */
object PermissionTable : DateBaseTable("auth.permission") {
    /** 권한 식별자 (비즈니스 키). 예: "READ", "WRITE", "DELETE", "MANAGE" */
    val name = varchar("name", 20).uniqueIndex()
    val displayName = varchar("display_name", 100)
}
