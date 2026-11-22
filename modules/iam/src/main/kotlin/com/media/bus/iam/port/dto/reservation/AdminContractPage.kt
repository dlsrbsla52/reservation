package com.media.bus.iam.port.dto.reservation

/** 관리자 계약 목록 한 페이지 (`ReservationAdminPort` 응답) */
data class AdminContractPage(
    val items: List<AdminContractView>?,
    val totalCnt: Long?,
    val pageRows: Int?,
    val pageNum: Int?,
)
