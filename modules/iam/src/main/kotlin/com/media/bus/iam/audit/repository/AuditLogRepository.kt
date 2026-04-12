package com.media.bus.iam.audit.repository

import com.media.bus.iam.audit.entity.AuditLogEntity
import com.media.bus.iam.audit.entity.AuditLogTable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.springframework.stereotype.Repository
import java.util.*

/**
 * ## 감사 로그 Repository
 *
 * 특정 회원 또는 action 단위 조회용. 대량 조회가 필요한 경우 별도 페이지네이션/기간 필터를 추가한다.
 */
@Repository
class AuditLogRepository {

    /** 특정 회원(actor)의 최근 감사 이력. */
    fun findByActorId(actorId: UUID, limit: Int = 100): List<AuditLogEntity> =
        AuditLogEntity
            .find { AuditLogTable.actorId eq actorId.toString() }
            .orderBy(AuditLogTable.createdAt to SortOrder.DESC)
            .limit(limit)
            .toList()

    /** 특정 action의 최근 이력 — 로그인 실패 모니터링 등에 활용. */
    fun findByAction(action: String, limit: Int = 100): List<AuditLogEntity> =
        AuditLogEntity
            .find { AuditLogTable.action eq action }
            .orderBy(AuditLogTable.createdAt to SortOrder.DESC)
            .limit(limit)
            .toList()
}
