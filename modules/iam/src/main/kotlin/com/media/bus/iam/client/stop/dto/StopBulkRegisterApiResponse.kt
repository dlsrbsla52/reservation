package com.media.bus.iam.client.stop.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/** stop 서비스 정류소 일괄 등록 API(`/register/bulk`)의 역직렬화용 DTO */
@JsonIgnoreProperties(ignoreUnknown = true)
data class StopBulkRegisterApiResponse(val data: StopBulkRegisterInfo?) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class StopBulkRegisterInfo(
        val savedCount: Int = 0,
        val updatedCount: Int = 0,
        val skippedCount: Int = 0,
        val failedCount: Int = 0,
        val totalCount: Int = 0,
    )
}
