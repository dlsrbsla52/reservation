package com.media.bus.iam.port.dto.reservation

/** 관리자 예약 목록 한 페이지 (`ReservationAdminPort` 응답) */
data class AdminReservationPage(
    val items: List<AdminReservationView>?,
    val totalCnt: Long?,
    val pageRows: Int?,
    val pageNum: Int?,
)
