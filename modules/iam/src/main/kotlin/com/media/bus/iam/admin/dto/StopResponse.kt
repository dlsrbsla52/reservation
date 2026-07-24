package com.media.bus.iam.admin.dto

import com.media.bus.iam.client.stop.dto.StopInfo
import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime
import java.util.*

@Schema(description = "정류소 응답")
data class StopResponse(
    @param:Schema(description = "정류소 pk(UUID)", example = "018f1e2a-0000-7000-8000-000000000001")
    val id: UUID,
    @param:Schema(description = "정류소 번호", example = "12345")
    val stopId: String,
    @param:Schema(description = "정류소 이름", example = "강남역")
    val stopName: String,
    @param:Schema(description = "경도 WGS84", example = "127.0627647339")
    val xCrd: String,
    @param:Schema(description = "위도 WGS84", example = "37.6472686025")
    val yCrd: String,
    @param:Schema(description = "노드 ID", example = "110000669")
    val nodeId: String,
    @param:Schema(description = "정류소 유형", example = "ROADSIDE_TIMED")
    val stopsType: String,
    @param:Schema(description = "생성일시", example = "2026-03-15 08:03:28.405709 +00:00")
    val createdAt: OffsetDateTime,
    @param:Schema(description = "수정일시", example = "2026-03-15 08:03:28.405709 +00:00")
    val updatedAt: OffsetDateTime,
) {
    companion object {
        fun of(info: StopInfo): StopResponse = StopResponse(
            id = info.id,
            stopId = info.stopId,
            stopName = info.stopName,
            xCrd = info.xCrd,
            yCrd = info.yCrd,
            nodeId = info.nodeId,
            stopsType = info.stopsType,
            createdAt = info.createdAt,
            updatedAt = info.updatedAt,
        )
    }
}
