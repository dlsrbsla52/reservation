package com.media.bus.iam.auth.repository

import com.media.bus.iam.auth.entity.RoleEntity
import com.media.bus.iam.auth.entity.RoleTable
import org.jetbrains.exposed.v1.core.eq
import org.springframework.stereotype.Repository
import java.util.*

/** 역할 마스터 Exposed Repository */
@Repository
class RoleRepository {

    fun findAll(): List<RoleEntity> = RoleEntity.all().toList()

    fun findById(id: UUID): RoleEntity? = RoleEntity.findById(id)

    fun findByName(name: String): RoleEntity? =
        RoleEntity.find { RoleTable.name eq name }.firstOrNull()
}
