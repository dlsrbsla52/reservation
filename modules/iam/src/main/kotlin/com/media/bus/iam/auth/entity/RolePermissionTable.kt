package com.media.bus.iam.auth.entity

import com.media.bus.common.entity.common.DateBaseTable

/**
 * ## 역할-권한 매핑 테이블 정의
 *
 * 스키마: `auth`, 테이블: `role_permission`
 * DB가 권한의 단일 source of truth — 코드 재배포 없이 운영 중 권한 변경이 가능하다.
 * `(role_id, permission_id)` 조합은 UNIQUE 제약으로 유일성을 보장한다.
 */
object RolePermissionTable : DateBaseTable("auth.role_permission") {
    val roleId = reference("role_id", RoleTable)
    val permissionId = reference("permission_id", PermissionTable)

    init {
        uniqueIndex("uq_role_permission", roleId, permissionId)
    }
}
