package com.media.bus.iam.admin.entity

import com.media.bus.common.entity.common.BaseTable
import com.media.bus.iam.member.entity.MemberTable
import com.media.bus.iam.member.entity.enumerated.MemberStatus
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * ## 회원 상태 변경 이력 테이블 정의
 *
 * 스키마: `auth`, 테이블: `member_status_history`
 * 계정 정지/해제 시 사유와 처리자를 기록한다.
 *
 * `changedBy`는 MemberTable을 FK 참조하여 `java.util.UUID` 호환성을 유지한다.
 * Exposed의 `uuid()` 컬럼은 Kotlin 2.3+에서 `kotlin.uuid.Uuid`를 반환하므로 사용하지 않는다.
 */
object MemberStatusHistoryTable : BaseTable("auth.member_status_history") {
    /** 대상 회원 */
    val memberId = reference("member_id", MemberTable, onDelete = ReferenceOption.CASCADE)
    val previousStatus = enumerationByName<MemberStatus>("previous_status", 20)
    val newStatus = enumerationByName<MemberStatus>("new_status", 20)
    val reason = varchar("reason", 500)
    /** 처리자(관리자) — MemberTable FK 참조로 java.util.UUID 호환 유지 */
    val changedBy = reference("changed_by", MemberTable)
    val createdAt = timestampWithTimeZone("created_at")
        .also { it.defaultValueFun = { OffsetDateTime.now(ZoneId.of("Asia/Seoul")) } }
}
