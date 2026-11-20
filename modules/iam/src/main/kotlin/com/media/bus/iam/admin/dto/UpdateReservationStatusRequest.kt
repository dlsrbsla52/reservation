package com.media.bus.iam.admin.dto

/** 예약 상태 변경 요청 (관리자 API 본문) */
data class UpdateReservationStatusRequest(
    val status: String,
    val note: String?,
)
