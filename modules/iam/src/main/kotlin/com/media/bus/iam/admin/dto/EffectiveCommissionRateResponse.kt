package com.media.bus.iam.admin.dto

import java.math.BigDecimal
import java.util.*

/** 계약에 적용할 최종 정산 비율 응답 (Reservation S2S 내부 API 용) */
data class EffectiveCommissionRateResponse(
    val memberId: UUID,
    val contractId: UUID?,
    val effectiveRate: BigDecimal,
)
