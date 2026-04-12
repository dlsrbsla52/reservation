package com.media.bus.iam.audit.entity

import com.media.bus.common.entity.common.BaseTable
import com.media.bus.iam.audit.entity.enumerated.AuditActorType
import com.media.bus.iam.audit.entity.enumerated.AuditResult
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * ## 감사 로그 테이블 정의
 *
 * 스키마: `audit`, 테이블: `audit_log`
 *
 * 민감 작업(로그인, 권한 변경, 계정 상태 전이, 비밀번호 변경 등) 이벤트를 기록한다.
 * 외래키는 설정하지 않는다 — 감사 로그는 `member` 삭제 이후에도 원본 보존되어야 하므로
 * 관계 유지보다 이력 영속성을 우선한다.
 */
object AuditLogTable : BaseTable("audit.audit_log") {
    /**
     * 행위 주체 회원 ID — 미인증 이벤트(로그인 실패 등)는 null.
     *
     * `java.util.UUID` 호환을 위해 문자열(UUID) 컬럼으로 저장한다.
     * Exposed v1 / Kotlin 2.3+ 조합에서 `uuid()` 헬퍼가 `kotlin.uuid.Uuid`를 반환하여
     * 기존 `java.util.UUID` 기반 API와 불일치하는 이슈를 회피하기 위함이다.
     * DB 컬럼 타입은 `uuid`이나 Exposed 측에서는 문자열로 바인딩한다.
     */
    val actorId = varchar("actor_id", 36).nullable()
    val actorType = enumerationByName<AuditActorType>("actor_type", 20)
    val action = varchar("action", 50)
    val targetType = varchar("target_type", 50).nullable()
    val targetId = varchar("target_id", 100).nullable()
    val ip = varchar("ip", 45).nullable()
    val userAgent = varchar("user_agent", 500).nullable()
    val result = enumerationByName<AuditResult>("result", 10)
    /** 추가 상세 정보 — JSON 문자열 (PostgreSQL jsonb / H2 clob) */
    val detail = text("detail").nullable()
    val createdAt = timestampWithTimeZone("created_at")
        .also { it.defaultValueFun = { OffsetDateTime.now(ZoneId.of("Asia/Seoul")) } }
}
