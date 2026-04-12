package com.media.bus.iam.audit.entity.enumerated

import com.media.bus.common.entity.common.BaseEnum

/** 감사 이벤트 처리 결과 — 성공/실패만 구분한다. */
@Suppress("unused")
enum class AuditResult(
    override val displayName: String,
) : BaseEnum {
    SUCCESS("성공"),
    FAILURE("실패");

    companion object {
        fun fromName(name: String): AuditResult? = BaseEnum.fromName<AuditResult>(name)
    }
}
