package com.media.bus.iam.auth.entity

import com.media.bus.common.entity.common.DateBaseTable

/** 역할-권한 매핑 테이블 정의 */
object RolePermissionTable : DateBaseTable("auth.role_permission") {
    val roleId = reference("role_id", RoleTable)
    val permissionId = reference("permission_id", PermissionTable)

    init {
        uniqueIndex("uq_role_permission", roleId, permissionId)
    }
}
