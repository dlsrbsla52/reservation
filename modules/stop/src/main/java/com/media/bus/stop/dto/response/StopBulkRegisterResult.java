package com.media.bus.stop.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "버스 정류소 일괄 등록 결과")
public record StopBulkRegisterResult(

    @Schema(description = "신규 저장된 정류소 수")
    int savedCount,

    @Schema(description = "변경 감지로 업데이트된 정류소 수")
    int updatedCount,

    @Schema(description = "변경 없이 건너뜀 정류소 수")
    int skippedCount,

    @Schema(description = "공공 API 전체 정류소 수")
    int totalCount

) {}
