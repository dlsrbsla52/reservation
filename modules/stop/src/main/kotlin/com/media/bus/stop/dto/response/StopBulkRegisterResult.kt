package com.media.bus.stop.dto.response

import io.swagger.v3.oas.annotations.media.Schema

/**
 * ## 버스 정류소 일괄 등록 결과 DTO
 */
@Schema(description = "버스 정류소 일괄 등록 결과")
data class StopBulkRegisterResult(
    @param:Schema(description = "신규 저장된 정류소 수")
    val savedCount: Int,

    @param:Schema(description = "변경 감지로 업데이트된 정류소 수")
    val updatedCount: Int,

    @param:Schema(description = "변경 없이 건너뜀 정류소 수")
    val skippedCount: Int,

    @Schema(description = "공공 API 전체 정류소 수")
    val totalCount: Int,
)
