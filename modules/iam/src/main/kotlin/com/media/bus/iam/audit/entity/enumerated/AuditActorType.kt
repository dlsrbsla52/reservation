package com.media.bus.iam.audit.entity.enumerated

import com.media.bus.common.entity.common.BaseEnum

/** 감사 로그 행위 주체 유형 */
@Suppress("unused")
enum class AuditActorType(
    override val displayName: String,
) : BaseEnum {
    MEMBER("일반 회원"),
    ADMIN("관리자"),
    SYSTEM("시스템"),
    ANONYMOUS("미인증");

    companion object {
        fun fromName(name: String): AuditActorType? = BaseEnum.fromName<AuditActorType>(name)
    }
}
