package com.media.bus.iam.audit.entity

import com.media.bus.common.entity.common.BaseTable
import com.media.bus.iam.audit.entity.enumerated.AuditActorType
import com.media.bus.iam.audit.entity.enumerated.AuditResult
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import java.time.OffsetDateTime
import java.time.ZoneId

/** 감사 로그 테이블 정의 */
object AuditLogTable : BaseTable("audit.audit_log") {
    val actorId = varchar("actor_id", 36).nullable()
    val actorType = enumerationByName<AuditActorType>("actor_type", 20)
    val action = varchar("action", 50)
    val targetType = varchar("target_type", 50).nullable()
    val targetId = varchar("target_id", 100).nullable()
    val ip = varchar("ip", 45).nullable()
    val userAgent = varchar("user_agent", 500).nullable()
    val result = enumerationByName<AuditResult>("result", 10)
    val detail = jsonbString("detail").nullable()
    val createdAt = timestampWithTimeZone("created_at")
        .also { it.defaultValueFun = { OffsetDateTime.now(ZoneId.of("Asia/Seoul")) } }
}
