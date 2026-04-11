package com.media.bus.iam.admin.entity

import com.media.bus.common.entity.common.BaseEntity
import com.media.bus.common.entity.common.UuidV7
import com.media.bus.iam.member.entity.MemberEntity
import com.media.bus.iam.member.entity.enumerated.MemberStatus
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.java.UUIDEntityClass
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.*

/**
 * ## 회원 상태 변경 이력 Exposed DAO 엔티티
 *
 * 계정 정지/해제 시 사유와 처리자를 기록한다.
 * 팩토리 메서드 `create()`로만 생성을 허용한다.
 */
class MemberStatusHistoryEntity(id: EntityID<UUID>) : BaseEntity(id) {

    companion object : UUIDEntityClass<MemberStatusHistoryEntity>(MemberStatusHistoryTable) {

        /** 상태 변경 이력을 생성한다. */
        fun create(
            member: MemberEntity,
            previousStatus: MemberStatus,
            newStatus: MemberStatus,
            reason: String,
            changedBy: MemberEntity,
        ): MemberStatusHistoryEntity = new(UuidV7.generate()) {
            this.member = member
            this.previousStatus = previousStatus
            this.newStatus = newStatus
            this.reason = reason
            this.changedBy = changedBy
            this.createdAt = OffsetDateTime.now(ZoneId.of("Asia/Seoul"))
        }
    }

    var member by MemberEntity referencedOn MemberStatusHistoryTable.memberId
    var previousStatus by MemberStatusHistoryTable.previousStatus
    var newStatus by MemberStatusHistoryTable.newStatus
    var reason by MemberStatusHistoryTable.reason
    var changedBy by MemberEntity referencedOn MemberStatusHistoryTable.changedBy
    var createdAt by MemberStatusHistoryTable.createdAt
}
