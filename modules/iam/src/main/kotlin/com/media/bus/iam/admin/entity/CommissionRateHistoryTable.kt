package com.media.bus.iam.admin.entity

import com.media.bus.common.entity.common.BaseTable
import com.media.bus.iam.admin.entity.enums.CommissionChangeType
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * ## 정산 비율 변경 이력 테이블
 *
 * 스키마: `auth`, 테이블: `commission_rate_history`
 * 기본율 변경 / 계약 오버라이드를 하나의 이력으로 통합 기록한다 (append-only).
 * `contract_id`가 NULL이면 기본율 변경, NOT NULL이면 계약별 오버라이드.
 * 모든 UUID 컬럼은 해당 테이블을 논리적으로 참조한다 (DB FK 미설정, 애플리케이션 레벨 관리).
 */
object CommissionRateHistoryTable : BaseTable("auth.commission_rate_history") {
    /** 대상 영업사원 — auth.member.id 논리적 참조 */
    val memberId = javaUUID("member_id").index("idx_commission_rate_history_member_id")
    /** 계약 ID — 오버라이드 시에만 채워짐 (NULL = 기본율 변경) */
    val contractId = javaUUID("contract_id").nullable().index("idx_commission_rate_history_contract_id")
    /** 변경 유형 */
    val changeType = enumerationByName<CommissionChangeType>("change_type", 20)
    /** 변경 전 정산 비율(%) — 최초 생성 시 NULL */
    val previousRate = decimal("previous_rate", 5, 2).nullable()
    /** 변경 후 정산 비율(%) */
    val newRate = decimal("new_rate", 5, 2)
    /** 변경 사유 (선택) */
    val reason = varchar("reason", 500).nullable()
    /** 변경한 Master 회원 — auth.member.id 논리적 참조 */
    val changedBy = javaUUID("changed_by")
    val createdAt = timestampWithTimeZone("created_at")
        .also { it.defaultValueFun = { OffsetDateTime.now(ZoneId.of("Asia/Seoul")) } }
}
