package com.media.bus.iam.auth.entity

import com.media.bus.common.entity.common.DateBaseEntity
import com.media.bus.common.entity.common.UuidV7
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.java.UUIDEntityClass
import java.util.*

/** 역할-권한 매핑 Exposed DAO 엔티티 */
class RolePermissionEntity(id: EntityID<UUID>) : DateBaseEntity(id, RolePermissionTable) {

    companion object : UUIDEntityClass<RolePermissionEntity>(RolePermissionTable) {

        fun create(role: RoleEntity, permission: PermissionEntity): RolePermissionEntity = new(UuidV7.generate()) {
            this.role = role
            this.permission = permission
        }
    }

    var role by RoleEntity referencedOn RolePermissionTable.roleId
    var permission by PermissionEntity referencedOn RolePermissionTable.permissionId
}
