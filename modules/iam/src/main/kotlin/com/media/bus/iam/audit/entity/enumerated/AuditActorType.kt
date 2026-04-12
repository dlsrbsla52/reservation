package com.media.bus.iam.audit.entity.enumerated

import com.media.bus.common.entity.common.BaseEnum

/**
 * ## 감사 로그 행위 주체 유형
 *
 * - `MEMBER`: 일반 회원이 수행한 작업
 * - `ADMIN`: 관리자 계정이 수행한 작업
 * - `SYSTEM`: 시스템(스케줄러, 배치) 자동 수행
 * - `ANONYMOUS`: 미인증 요청 (로그인 실패, 비밀번호 초기화 요청 등)
 */
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
