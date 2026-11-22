package com.media.bus.iam.admin.entity.enums

import com.media.bus.common.entity.common.BaseEnum

/** 정산 비율 변경 유형 */
enum class CommissionChangeType(
    override val displayName: String,
) : BaseEnum {
    DEFAULT_RATE("기본율 변경"),
    CONTRACT_OVERRIDE("계약별 오버라이드"),
    ;

    companion object {
        fun fromName(name: String): CommissionChangeType? = BaseEnum.fromName<CommissionChangeType>(name)
    }
}
