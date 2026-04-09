package com.media.bus.iam.member.entity.enumerated

import com.media.bus.common.entity.common.BaseEnum

/**
 * ## 회원 계정 상태 Enum
 *
 * - `ACTIVE`: 정상 활성 상태
 * - `SUSPENDED`: 관리자에 의해 이용 정지된 상태
 * - `WITHDRAWN`: 회원 본인이 자발적으로 탈퇴한 상태
 *
 * auth 모듈 내부에서만 사용되는 도메인 개념이다.
 * JWT 클레임에는 포함되지 않으며, 다른 서비스로 노출되지 않는다.
 */
@Suppress("unused")
enum class MemberStatus(
    override val displayName: String,
) : BaseEnum {
    ACTIVE("정상 활성 상태"),
    SUSPENDED("관리자에 의해 이용 정지된 상태"),
    WITHDRAWN("회원 본인이 자발적으로 탈퇴한 상태");

    companion object {
        fun fromName(name: String): MemberStatus? = BaseEnum.fromName<MemberStatus>(name)
    }
}
