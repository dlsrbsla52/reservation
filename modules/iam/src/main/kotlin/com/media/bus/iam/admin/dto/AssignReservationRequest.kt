package com.media.bus.iam.admin.dto

import java.util.*

/** 예약 담당자 지정 요청 (관리자 API 본문). */
data class AssignReservationRequest(
    val assigneeId: UUID,
)
