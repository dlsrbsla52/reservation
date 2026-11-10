package com.media.bus.iam.client.reservation.dto

import jakarta.validation.constraints.NotNull
import java.time.OffsetDateTime

/**
 * ## 선점 기간 연장 요청 (reservation 측 `ExtendHoldRequest` 의 iam 내부 사본)
 *
 * 필드명·JSON 구조는 reservation 측과 반드시 일치해야 한다.
 */
data class ExtendHoldRequest(
    @field:NotNull
    val holdExpiresAt: OffsetDateTime,
)
