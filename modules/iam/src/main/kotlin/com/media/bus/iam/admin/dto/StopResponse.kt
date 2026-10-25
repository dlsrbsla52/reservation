package com.media.bus.iam.admin.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.media.bus.iam.client.stop.dto.StopInfo
import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
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
    // stop 모듈 BusStopResponse 와 동일한 이유로 직렬화명을 고정한다.
    // (게터가 getXCrd() 라서 Jackson 기본 네이밍은 xcrd 로 내보낸다 — OpenAPI 문서와 불일치)
    @get:JsonProperty("xCrd")
    @get:Schema(description = "경도 WGS84", example = "127.0627647")
    val xCrd: String,
    @get:JsonProperty("yCrd")
    @get:Schema(description = "위도 WGS84", example = "37.6472686")
    val yCrd: String,
    @param:Schema(description = "노드 ID", example = "110000669")
    val nodeId: String,
    @param:Schema(description = "정류소 유형", example = "ROADSIDE_TIMED")
    val stopsType: String,
    @param:Schema(description = "생성일시", example = "2026-03-15 08:03:28.405709 +00:00")
    val createdAt: OffsetDateTime,
    @param:Schema(description = "수정일시", example = "2026-03-15 08:03:28.405709 +00:00")
    val updatedAt: OffsetDateTime,
    @param:Schema(
        description = "정류소 현재 단가. 단가 미등록 정류소는 null이며 0원과 구분된다.",
        example = "1500.00",
        nullable = true,
    )
    val unitPrice: BigDecimal?,
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
            unitPrice = info.unitPrice,
        )
    }
}
