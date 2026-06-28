package com.media.bus.iam.admin.repository

import com.media.bus.iam.admin.entity.ManagerCommissionEntity
import com.media.bus.iam.admin.entity.ManagerCommissionTable
import org.jetbrains.exposed.v1.core.eq
import org.springframework.stereotype.Repository
import java.util.*

/**
 * ## 영업사원 기본 정산 비율 Repository
 */
@Repository
class ManagerCommissionRepository {

    /** 영업사원 ID로 기본 정산 비율 단건을 조회한다. */
    fun findByMemberId(memberId: UUID): ManagerCommissionEntity? =
        ManagerCommissionEntity
            .find { ManagerCommissionTable.memberId eq memberId }
            .firstOrNull()

    /** 영업사원 ID로 기본 정산 비율 레코드 존재 여부를 확인한다. */
    fun existsByMemberId(memberId: UUID): Boolean =
        ManagerCommissionEntity
            .find { ManagerCommissionTable.memberId eq memberId }
            .count() > 0
}
