package com.media.bus.iam.admin.entity

import com.media.bus.common.entity.common.BaseTable
import com.media.bus.iam.member.entity.MemberTable
import com.media.bus.iam.member.entity.enumerated.MemberStatus
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import java.time.OffsetDateTime
import java.time.ZoneId

/** 회원 상태 변경 이력 테이블 정의 */
object MemberStatusHistoryTable : BaseTable("auth.member_status_history") {
    val memberId = reference("member_id", MemberTable, onDelete = ReferenceOption.CASCADE)
    val previousStatus = enumerationByName<MemberStatus>("previous_status", 20)
    val newStatus = enumerationByName<MemberStatus>("new_status", 20)
    val reason = varchar("reason", 500)
    val changedBy = reference("changed_by", MemberTable)
    val createdAt = timestampWithTimeZone("created_at")
        .also { it.defaultValueFun = { OffsetDateTime.now(ZoneId.of("Asia/Seoul")) } }
}
