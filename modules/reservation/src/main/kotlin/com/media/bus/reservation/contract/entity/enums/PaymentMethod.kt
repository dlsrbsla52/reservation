package com.media.bus.reservation.contract.entity.enums

import com.media.bus.common.entity.common.BaseEnum

/**
 * ## 납부 방법 Enum
 *
 * - BANK_TRANSFER: 계좌이체
 * - CARD: 카드
 * - CASH: 현금
 */
@Suppress("unused")
enum class PaymentMethod(
    override val displayName: String,
) : BaseEnum {
    BANK_TRANSFER("계좌이체"),
    CARD("카드"),
    CASH("현금"),
    ;

    companion object {
        fun fromName(name: String): PaymentMethod? = BaseEnum.fromName<PaymentMethod>(name)
    }
}
