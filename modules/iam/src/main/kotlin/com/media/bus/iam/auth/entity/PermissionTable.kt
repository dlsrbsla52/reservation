package com.media.bus.iam.auth.entity

import com.media.bus.common.entity.common.DateBaseTable

/** 권한 마스터 테이블 정의 */
object PermissionTable : DateBaseTable("auth.permission") {
    val name = varchar("name", 20).uniqueIndex()
    val displayName = varchar("display_name", 100)
}
