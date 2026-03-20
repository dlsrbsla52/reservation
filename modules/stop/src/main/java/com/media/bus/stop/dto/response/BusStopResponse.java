package com.media.bus.stop.dto.response;

import com.media.bus.stop.entity.Stop;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "버스 정류장 데이터")
public record BusStopResponse(

    @Schema(description = "아이디 PK", example = "ac130005-9cf0-14ae-819c-f08586140000")
    UUID id,

    @Schema(description = "정류소 번호 (STOPS_NO)", example = "11833")
    String stopId,

    @Schema(description = "정류소 이름 (STOPS_NM)", example = "중계역")
    String stopName,

    @Schema(description = "경도 WGS84 (XCRD)", example = "127.0627647339")
    String xCrd,

    @Schema(description = "위도 WGS84 (YCRD)", example = "37.6472686025")
    String yCrd,

    @Schema(description = "노드 ID (NODE_ID)", example = "110000669")
    String nodeId,

    @Schema(description = "정류소 유형 (STOPS_TYPE)", example = "가로변시간")
    String stopsType,

    @Schema(description = "생성일시", example = "2026-03-15 08:03:28.405709 +00:00")
    OffsetDateTime createdAt,

    @Schema(description = "수정일시", example = "2026-03-15 08:03:28.405709 +00:00")
    OffsetDateTime updatedAt
) {

    public static BusStopResponse of(Stop stop) {
        return new BusStopResponse(
            stop.getId(),
            stop.getStopId(),
            stop.getStopName(),
            stop.getXCrd(),
            stop.getYCrd(),
            stop.getNodeId(),
            stop.getStopsType().getName(),
            stop.getCreatedAt(),
            stop.getUpdatedAt()
        );
    }

}
