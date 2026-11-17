package com.media.bus.iam.port.dto.stop

/**
 * ## 공공 API 정류소 일괄 등록 결과 (`StopAdminPort` 응답)
 *
 * stop 모듈의 `StopBulkRegisterResult`를 iam 쪽 타입으로 옮긴 것이다. 기본값을 두지 않아 어댑터가 집계 필드를 빠뜨리면 컴파일이 실패한다.
 */
data class StopBulkRegisterInfo(
    val savedCount: Int,
    val updatedCount: Int,
    val skippedCount: Int,
    val failedCount: Int,
    val totalCount: Int,
)
