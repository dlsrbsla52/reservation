package com.media.bus.iam.port.dto.stop

/** 공공 API 정류소 일괄 등록 결과 (`StopAdminPort` 응답) */
data class StopBulkRegisterInfo(
    val savedCount: Int,
    val updatedCount: Int,
    val skippedCount: Int,
    val failedCount: Int,
    val totalCount: Int,
)
