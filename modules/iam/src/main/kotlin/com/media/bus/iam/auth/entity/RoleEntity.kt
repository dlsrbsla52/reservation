package com.media.bus.iam.auth.entity

import com.media.bus.common.entity.common.DateBaseEntity
import com.media.bus.common.entity.common.UuidV7
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.java.UUIDEntityClass
import java.util.*

/** 역할 마스터 Exposed DAO 엔티티 */
class RoleEntity(id: EntityID<UUID>) : DateBaseEntity(id, RoleTable) {

    companion object : UUIDEntityClass<RoleEntity>(RoleTable) {

        fun create(name: String, displayName: String): RoleEntity = new(UuidV7.generate()) {
            this.name = name
            this.displayName = displayName
        }
    }

    var name by RoleTable.name
    var displayName by RoleTable.displayName
}
