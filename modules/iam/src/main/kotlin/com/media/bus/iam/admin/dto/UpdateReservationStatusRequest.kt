package com.media.bus.iam.admin.dto

/**
 * ## 예약 상태 변경 요청 (관리자 API 본문)
 *
 * `status`는 문자열로 받고 `app` 어댑터가 enum으로 바꾼다(알 수 없는 값 → 400). `note` 길이(1000자)는 reservation `ReservationFacade`가 검사한다.
 */
data class UpdateReservationStatusRequest(
    val status: String,
    val note: String?,
)
