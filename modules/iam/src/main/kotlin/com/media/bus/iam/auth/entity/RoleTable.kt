package com.media.bus.iam.auth.entity

import com.media.bus.common.entity.common.DateBaseTable

/** 역할 마스터 테이블 정의 */
object RoleTable : DateBaseTable("auth.role") {
    val name = varchar("name", 30).uniqueIndex()
    val displayName = varchar("display_name", 100)
}
