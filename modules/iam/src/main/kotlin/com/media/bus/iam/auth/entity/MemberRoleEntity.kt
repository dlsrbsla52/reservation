package com.media.bus.iam.auth.entity

import com.media.bus.common.entity.common.DateBaseEntity
import com.media.bus.common.entity.common.UuidV7
import com.media.bus.iam.member.entity.MemberEntity
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.java.UUIDEntityClass
import java.util.*

/**
 * ## 회원-역할 매핑 Exposed DAO 엔티티
 *
 * `MemberRoleTable`에 매핑되는 DAO 엔티티.
 * 생성은 정적 팩토리 메서드 `of()`를 통해서만 허용한다.
 */
class MemberRoleEntity(id: EntityID<UUID>) : DateBaseEntity(id, MemberRoleTable) {

    companion object : UUIDEntityClass<MemberRoleEntity>(MemberRoleTable) {

        fun of(member: MemberEntity, role: RoleEntity): MemberRoleEntity = new(UuidV7.generate()) {
            this.member = member
            this.role = role
        }
    }

    var member by MemberEntity referencedOn MemberRoleTable.memberId
    var role by RoleEntity referencedOn MemberRoleTable.roleId
}
