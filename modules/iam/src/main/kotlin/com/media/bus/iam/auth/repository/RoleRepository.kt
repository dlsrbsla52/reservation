package com.media.bus.iam.auth.repository

import com.media.bus.iam.auth.entity.RoleEntity
import com.media.bus.iam.auth.entity.RoleTable
import org.jetbrains.exposed.v1.core.eq
import org.springframework.stereotype.Repository
import java.util.*

/**
 * ## 역할 마스터 Exposed Repository
 *
 * 회원가입 시 `MemberType.name()`으로 Role 엔티티를 조회하는 데 사용한다.
 */
@Repository
class RoleRepository {

    fun findAll(): List<RoleEntity> = RoleEntity.all().toList()

    fun findById(id: UUID): RoleEntity? = RoleEntity.findById(id)

    fun findByName(name: String): RoleEntity? =
        RoleEntity.find { RoleTable.name eq name }.firstOrNull()
}
