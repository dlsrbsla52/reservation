package com.media.bus.iam.auth.repository

import com.media.bus.iam.auth.entity.MemberRoleEntity
import com.media.bus.iam.auth.entity.MemberRoleTable
import com.media.bus.iam.auth.entity.RoleTable
import com.media.bus.iam.member.entity.MemberTable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select
import org.springframework.stereotype.Repository
import java.util.*

/**
 * ## 회원-역할 매핑 Exposed Repository
 *
 * 로그인 및 토큰 재발급 시 해당 회원의 역할 정보를 조회한다.
 */
@Repository
class MemberRoleRepository {

    /**
     * 회원 ID로 역할 이름 집합을 조회한다 (Role 엔티티 JOIN).
     * Guard/Validator 계층 등 이름만 필요한 경우에 사용한다.
     */
    fun findRoleNamesByMemberId(memberId: UUID): Set<String> =
        (MemberRoleTable innerJoin RoleTable)
            .select(RoleTable.name)
            .where { MemberRoleTable.memberId eq EntityID(memberId, MemberTable) }
            .map { it[RoleTable.name] }
            .toSet()

    /**
     * 회원 ID로 MemberRole 목록을 조회한다.
     * 로그인/토큰 갱신/회원 정보 조회 시 사용한다.
     */
    fun findWithRoleByMemberId(memberId: UUID): List<MemberRoleEntity> =
        MemberRoleEntity.find { MemberRoleTable.memberId eq EntityID(memberId, MemberTable) }
            .toList()
}
