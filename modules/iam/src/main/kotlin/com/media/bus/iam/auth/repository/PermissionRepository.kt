package com.media.bus.iam.auth.repository

import com.media.bus.iam.auth.entity.PermissionEntity
import com.media.bus.iam.auth.entity.PermissionTable
import org.jetbrains.exposed.v1.core.eq
import org.springframework.stereotype.Repository

/**
 * ## 권한 마스터 Exposed Repository
 *
 * 권한 목록 조회 및 이름 기반 단건 조회를 제공한다.
 */
@Repository
class PermissionRepository {

    fun findAll(): List<PermissionEntity> = PermissionEntity.all().toList()

    /** 권한 이름(비즈니스 키)으로 단건 조회. 예: "READ", "WRITE", "DELETE", "MANAGE" */
    fun findByName(name: String): PermissionEntity? =
        PermissionEntity.find { PermissionTable.name eq name }.firstOrNull()
}
