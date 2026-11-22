package com.media.bus.iam.admin.dto

import jakarta.validation.constraints.NotNull
import java.time.OffsetDateTime

/** 선점 기간 연장 요청 (관리자 API 본문) */
data class ExtendHoldRequest(
    @field:NotNull
    val holdExpiresAt: OffsetDateTime,
)
