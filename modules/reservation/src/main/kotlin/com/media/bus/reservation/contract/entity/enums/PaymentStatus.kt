package com.media.bus.reservation.contract.entity.enums

import com.media.bus.common.entity.common.BaseEnum

/**
 * ## 납부 상태 Enum
 *
 * - UNPAID: 미납 (초기 기본값)
 * - PAID: 납부 완료
 * - OVERDUE: 연체
 */
@Suppress("unused")
enum class PaymentStatus(
    override val displayName: String,
) : BaseEnum {
    UNPAID("미납"),
    PAID("납부완료"),
    OVERDUE("연체"),
    ;

    companion object {
        fun fromName(name: String): PaymentStatus? = BaseEnum.fromName<PaymentStatus>(name)
    }
}
