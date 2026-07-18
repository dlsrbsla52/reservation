package com.media.bus.iam.admin.dto

import com.media.bus.iam.client.stop.dto.StopInfo
import io.swagger.v3.oas.annotations.media.Schema
import java.util.*

@Schema(description = "정류소 응답")
data class StopResponse(
    @param:Schema(description = "정류소 pk(UUID)", example = "018f1e2a-0000-7000-8000-000000000001")
    val id: UUID,
    @param:Schema(description = "정류소 번호", example = "12345")
    val stopId: String,
    @param:Schema(description = "정류소 이름", example = "강남역")
    val stopName: String,
) {
    companion object {
        fun of(info: StopInfo): StopResponse = StopResponse(
            id = info.id,
            stopId = info.stopId,
            stopName = info.stopName,
        )
    }
}
