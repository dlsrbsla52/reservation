package com.media.bus.iam.admin.repository

import com.media.bus.iam.admin.entity.MemberStatusHistoryEntity
import com.media.bus.iam.admin.entity.MemberStatusHistoryTable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.springframework.stereotype.Repository
import java.util.*

/**
 * ## 회원 상태 변경 이력 Repository
 *
 * 특정 회원의 정지/해제 이력을 조회한다.
 */
@Repository
class MemberStatusHistoryRepository {

    /** 특정 회원의 상태 변경 이력을 최신순으로 조회한다. */
    fun findByMemberId(memberId: UUID): List<MemberStatusHistoryEntity> =
        MemberStatusHistoryEntity
            .find { MemberStatusHistoryTable.memberId eq memberId }
            .orderBy(MemberStatusHistoryTable.createdAt to SortOrder.DESC)
            .toList()
}
