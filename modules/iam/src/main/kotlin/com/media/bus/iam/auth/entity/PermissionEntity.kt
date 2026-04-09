package com.media.bus.iam.auth.entity

import com.media.bus.common.entity.common.DateBaseEntity
import com.media.bus.common.entity.common.UuidV7
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.java.UUIDEntityClass
import java.util.*

/**
 * ## 권한 마스터 Exposed DAO 엔티티
 *
 * `PermissionTable`에 매핑되는 DAO 엔티티.
 * 생성은 정적 팩토리 메서드 `of()`를 통해서만 허용한다.
 */
class PermissionEntity(id: EntityID<UUID>) : DateBaseEntity(id, PermissionTable) {

    companion object : UUIDEntityClass<PermissionEntity>(PermissionTable) {

        fun of(name: String, displayName: String): PermissionEntity = new(UuidV7.generate()) {
            this.name = name
            this.displayName = displayName
        }
    }

    var name by PermissionTable.name
    var displayName by PermissionTable.displayName
}
