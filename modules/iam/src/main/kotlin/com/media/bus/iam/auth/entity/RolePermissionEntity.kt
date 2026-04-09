package com.media.bus.iam.auth.entity

import com.media.bus.common.entity.common.DateBaseEntity
import com.media.bus.common.entity.common.UuidV7
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.java.UUIDEntityClass
import java.util.*

/**
 * ## 역할-권한 매핑 Exposed DAO 엔티티
 *
 * `RolePermissionTable`에 매핑되는 DAO 엔티티.
 * 생성은 정적 팩토리 메서드 `of()`를 통해서만 허용한다.
 */
class RolePermissionEntity(id: EntityID<UUID>) : DateBaseEntity(id, RolePermissionTable) {

    companion object : UUIDEntityClass<RolePermissionEntity>(RolePermissionTable) {

        fun of(role: RoleEntity, permission: PermissionEntity): RolePermissionEntity = new(UuidV7.generate()) {
            this.role = role
            this.permission = permission
        }
    }

    var role by RoleEntity referencedOn RolePermissionTable.roleId
    var permission by PermissionEntity referencedOn RolePermissionTable.permissionId
}
