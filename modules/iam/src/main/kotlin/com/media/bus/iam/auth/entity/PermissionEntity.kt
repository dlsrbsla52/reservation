package com.media.bus.iam.auth.entity

import com.media.bus.common.entity.common.DateBaseEntity
import com.media.bus.common.entity.common.UuidV7
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.java.UUIDEntityClass
import java.util.*

/** 권한 마스터 Exposed DAO 엔티티 */
class PermissionEntity(id: EntityID<UUID>) : DateBaseEntity(id, PermissionTable) {

    companion object : UUIDEntityClass<PermissionEntity>(PermissionTable) {

        fun create(name: String, displayName: String): PermissionEntity = new(UuidV7.generate()) {
            this.name = name
            this.displayName = displayName
        }
    }

    var name by PermissionTable.name
    var displayName by PermissionTable.displayName
}
