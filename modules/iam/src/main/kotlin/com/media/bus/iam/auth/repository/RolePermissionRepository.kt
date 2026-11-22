package com.media.bus.iam.auth.repository

import com.media.bus.iam.auth.entity.*
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select
import org.springframework.stereotype.Repository
import java.util.*

/** 역할-권한 매핑 Exposed Repository */
@Repository
class RolePermissionRepository {

    /** 역할 이름으로 해당 역할에 매핑된 권한 이름 집합을 조회한다. */
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

    /** 역할 ID로 매핑된 권한(`PermissionEntity`) 목록을 단일 조인 쿼리로 조회한다. */
    fun findPermissionsByRoleId(roleId: UUID): List<PermissionEntity> =
        PermissionEntity.wrapRows(
            (PermissionTable innerJoin RolePermissionTable)
                .select(PermissionTable.columns)
                .where { RolePermissionTable.roleId eq EntityID(roleId, RoleTable) }
        ).toList()

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
