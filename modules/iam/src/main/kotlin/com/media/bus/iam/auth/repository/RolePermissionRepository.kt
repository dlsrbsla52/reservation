package com.media.bus.iam.auth.repository

import com.media.bus.iam.auth.entity.PermissionTable
import com.media.bus.iam.auth.entity.RolePermissionEntity
import com.media.bus.iam.auth.entity.RolePermissionTable
import com.media.bus.iam.auth.entity.RoleTable
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select
import org.springframework.stereotype.Repository
import java.util.*

/**
 * ## 역할-권한 매핑 Exposed Repository
 *
 * 로그인 및 토큰 재발급 시 해당 역할의 권한 이름 집합을 조회한다.
 * DB를 권한의 단일 source of truth로 사용하므로, 운영 중 권한 변경이 즉시 반영된다.
 * (단, 기존 발급된 Access Token의 TTL 내에서는 구 권한이 유지된다.)
 */
@Repository
class RolePermissionRepository {

    /**
     * 역할 이름으로 해당 역할에 매핑된 권한 이름 집합을 조회한다.
     * AuthService의 로그인/토큰 갱신 시 1회 호출된다.
     *
     * @param roleName `MemberType.name()` 값 (예: "ADMIN_USER")
     * @return 권한 이름 집합 (예: ["READ", "WRITE"])
     */
    fun findPermissionNamesByRoleName(roleName: String): Set<String> =
        (RolePermissionTable innerJoin RoleTable innerJoin PermissionTable)
            .select(PermissionTable.name)
            .where { RoleTable.name eq roleName }
            .map { it[PermissionTable.name] }
            .toSet()

    /** 역할 ID로 역할-권한 매핑 목록을 조회한다. */
    fun findByRoleId(roleId: UUID): List<RolePermissionEntity> =
        RolePermissionEntity.find { RolePermissionTable.roleId eq EntityID(roleId, RoleTable) }
            .toList()

    /** 역할 ID와 권한 이름으로 매핑 단건을 조회한다. 중복 할당 방지 및 해제 시 사용한다. */
    fun findByRoleIdAndPermissionName(roleId: UUID, permissionName: String): RolePermissionEntity? =
        RolePermissionEntity.wrapRows(
            (RolePermissionTable innerJoin PermissionTable)
                .select(RolePermissionTable.columns)
                .where {
                    (RolePermissionTable.roleId eq EntityID(roleId, RoleTable)) and
                        (PermissionTable.name eq permissionName)
                }
        ).firstOrNull()
}
