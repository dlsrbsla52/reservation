package com.media.bus.iam.admin.entity.enums

import com.media.bus.common.entity.common.BaseEnum

/**
 * ## 정산 비율 변경 유형
 *
 * - [DEFAULT_RATE]: 영업사원 기본 정산 비율 변경
 * - [CONTRACT_OVERRIDE]: 특정 계약에 대한 정산 비율 오버라이드
 */
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
