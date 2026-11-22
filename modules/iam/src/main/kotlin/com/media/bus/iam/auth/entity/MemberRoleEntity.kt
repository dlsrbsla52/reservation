package com.media.bus.iam.auth.entity

import com.media.bus.common.entity.common.DateBaseEntity
import com.media.bus.common.entity.common.UuidV7
import com.media.bus.iam.member.entity.MemberEntity
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.java.UUIDEntityClass
import java.util.*

/** 회원-역할 매핑 Exposed DAO 엔티티 */
class MemberRoleEntity(id: EntityID<UUID>) : DateBaseEntity(id, MemberRoleTable) {

    companion object : UUIDEntityClass<MemberRoleEntity>(MemberRoleTable) {

        fun create(member: MemberEntity, role: RoleEntity): MemberRoleEntity = new(UuidV7.generate()) {
            this.member = member
            this.role = role
        }
    }

    var member by MemberEntity referencedOn MemberRoleTable.memberId
    var role by RoleEntity referencedOn MemberRoleTable.roleId
}
