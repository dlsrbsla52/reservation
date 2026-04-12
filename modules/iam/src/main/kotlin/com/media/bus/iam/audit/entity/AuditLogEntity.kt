package com.media.bus.iam.audit.entity

import com.media.bus.common.entity.common.BaseEntity
import com.media.bus.common.entity.common.UuidV7
import com.media.bus.iam.audit.entity.enumerated.AuditActorType
import com.media.bus.iam.audit.entity.enumerated.AuditResult
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.java.UUIDEntityClass
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.*

/**
 * ## 감사 로그 Exposed DAO 엔티티
 *
 * 민감 작업 추적용 이벤트 로그. 팩토리 메서드 `create()`로만 생성을 허용한다.
 * 신규 필드를 추가할 경우 Liquibase changelog 및 [AuditActorType], [AuditResult] 확장을 고려한다.
 */
class AuditLogEntity(id: EntityID<UUID>) : BaseEntity(id) {

    companion object : UUIDEntityClass<AuditLogEntity>(AuditLogTable) {

        /** 감사 로그 생성 — 이벤트 발생 시점에 즉시 기록한다. */
        fun create(
            actorId: UUID?,
            actorType: AuditActorType,
            action: String,
            targetType: String? = null,
            targetId: String? = null,
            ip: String? = null,
            userAgent: String? = null,
            result: AuditResult,
            detail: String? = null,
        ): AuditLogEntity = new(UuidV7.generate()) {
            this.actorId = actorId?.toString()
            this.actorType = actorType
            this.action = action
            this.targetType = targetType
            this.targetId = targetId
            this.ip = ip
            this.userAgent = userAgent
            this.result = result
            this.detail = detail
            this.createdAt = OffsetDateTime.now(ZoneId.of("Asia/Seoul"))
        }
    }

    var actorId by AuditLogTable.actorId
    var actorType by AuditLogTable.actorType
    var action by AuditLogTable.action
    var targetType by AuditLogTable.targetType
    var targetId by AuditLogTable.targetId
    var ip by AuditLogTable.ip
    var userAgent by AuditLogTable.userAgent
    var result by AuditLogTable.result
    var detail by AuditLogTable.detail
    var createdAt by AuditLogTable.createdAt
}
