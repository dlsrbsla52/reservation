package com.media.bus.reservation.contract.entity.enums

import com.media.bus.common.entity.common.BaseEnum

/**
 * ## 계약 상태 Enum
 *
 * - PENDING: 계약 대기 (초기 생성 시 기본값)
 * - ACTIVE: 계약 활성 (승인 완료)
 * - EXPIRED: 계약 만료 (계약 기간 종료)
 * - CANCELLED: 계약 취소
 */
@Suppress("unused")
enum class ContractStatus(
    override val displayName: String,
) : BaseEnum {
    PENDING("대기"),
    ACTIVE("활성"),
    EXPIRED("만료"),
    CANCELLED("취소됨"),
    ;

    companion object {
        fun fromName(name: String): ContractStatus? = BaseEnum.fromName<ContractStatus>(name)
    }
}
