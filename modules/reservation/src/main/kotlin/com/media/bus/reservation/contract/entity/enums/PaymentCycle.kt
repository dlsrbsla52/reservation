package com.media.bus.reservation.contract.entity.enums

import com.media.bus.common.entity.common.BaseEnum

/**
 * ## 납부 주기 Enum
 *
 * - MONTHLY: 월납
 * - QUARTERLY: 분기납
 * - ANNUALLY: 연납
 */
@Suppress("unused")
enum class PaymentCycle(
    override val displayName: String,
) : BaseEnum {
    MONTHLY("월납"),
    QUARTERLY("분기납"),
    ANNUALLY("연납"),
    ;

    companion object {
        fun fromName(name: String): PaymentCycle? = BaseEnum.fromName<PaymentCycle>(name)
    }
}
