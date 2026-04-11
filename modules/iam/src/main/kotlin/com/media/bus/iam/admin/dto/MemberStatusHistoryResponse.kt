package com.media.bus.iam.admin.dto

import com.media.bus.iam.admin.entity.MemberStatusHistoryEntity
import com.media.bus.iam.member.entity.enumerated.MemberStatus
import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime
import java.util.*

/** 회원 상태 변경 이력 응답 DTO */
@Schema(description = "회원 상태 변경 이력 응답 DTO")
data class MemberStatusHistoryResponse(

    @Schema(description = "이력 ID")
    val id: UUID,

    @Schema(description = "변경 전 상태")
    val previousStatus: MemberStatus,

    @Schema(description = "변경 후 상태")
    val newStatus: MemberStatus,

    @Schema(description = "변경 사유")
    val reason: String,

    @Schema(description = "처리자 ID")
    val changedBy: UUID,

    @Schema(description = "변경 일시")
    val createdAt: OffsetDateTime,
) {
    companion object {
        fun of(entity: MemberStatusHistoryEntity): MemberStatusHistoryResponse =
            MemberStatusHistoryResponse(
                id = entity.id.value,
                previousStatus = entity.previousStatus,
                newStatus = entity.newStatus,
                reason = entity.reason,
                changedBy = entity.changedBy.id.value,
                createdAt = entity.createdAt,
            )
    }
}
