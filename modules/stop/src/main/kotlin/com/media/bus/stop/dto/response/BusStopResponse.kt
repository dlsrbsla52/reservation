package com.media.bus.stop.dto.response

import com.media.bus.stop.entity.StopEntity
import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime
import java.util.*

/**
 * ## 버스 정류장 응답 DTO
 */
@Schema(description = "버스 정류장 데이터")
data class BusStopResponse(
    @Schema(description = "아이디 PK", example = "ac130005-9cf0-14ae-819c-f08586140000")
    val id: UUID,

    @Schema(description = "정류소 번호 (STOPS_NO)", example = "11833")
    val stopId: String,

    @Schema(description = "정류소 이름 (STOPS_NM)", example = "중계역")
    val stopName: String,

    @Schema(description = "경도 WGS84 (XCRD)", example = "127.0627647339")
    val xCrd: String,

    @Schema(description = "위도 WGS84 (YCRD)", example = "37.6472686025")
    val yCrd: String,

    @Schema(description = "노드 ID (NODE_ID)", example = "110000669")
    val nodeId: String,

    @Schema(description = "정류소 유형 (STOPS_TYPE)", example = "가로변시간")
    val stopsType: String,

    @Schema(description = "생성일시", example = "2026-03-15 08:03:28.405709 +00:00")
    val createdAt: OffsetDateTime,

    @Schema(description = "수정일시", example = "2026-03-15 08:03:28.405709 +00:00")
    val updatedAt: OffsetDateTime,
) {
    companion object {
        /** StopEntity -> BusStopResponse 변환 팩토리 */
        fun of(stop: StopEntity): BusStopResponse = BusStopResponse(
            id = stop.id.value,
            stopId = stop.stopId,
            stopName = stop.stopName,
            xCrd = stop.xCrd,
            yCrd = stop.yCrd,
            nodeId = stop.nodeId,
            stopsType = stop.stopsType.name,
            createdAt = stop.createdAt,
            updatedAt = stop.updatedAt,
        )
    }
}
