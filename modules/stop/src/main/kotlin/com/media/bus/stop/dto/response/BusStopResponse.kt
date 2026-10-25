package com.media.bus.stop.dto.response

import com.fasterxml.jackson.annotation.JsonProperty
import com.media.bus.stop.entity.StopEntity
import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.*

/**
 * ## 버스 정류장 응답 DTO
 *
 * **어노테이션 사이트 타깃 주의 (`@get:`)**
 *
 * 이전에는 `@param:Schema` 를 사용해 두 가지 문제가 있었다.
 * 1. springdoc 이 생성자 파라미터에서만 스키마를 인식해 OpenAPI 상 `writeOnly: true` 로 문서화되었다.
 *    즉 문서만 보면 응답에 좌표가 없는 것처럼 보였다.
 * 2. Jackson 은 게터명에서 프로퍼티명을 유도하는데, `xCrd` 의 게터는 `getXCrd()` 이고
 *    앞쪽 연속 대문자는 소문자로 변환되어 **실제 직렬화명이 `xcrd`** 가 되었다.
 *    결과적으로 문서(`xCrd`)와 실제 응답(`xcrd`)이 어긋나, OpenAPI 로 생성한 프론트
 *    클라이언트는 좌표를 항상 null 로 받았다.
 *
 * 그래서 모든 필드를 `@get:Schema` 로 옮기고, 좌표에는 `@get:JsonProperty` 로 직렬화명을 못 박았다.
 * 계약은 `BusStopResponseSerializationTest` 가 회귀 방지로 고정한다.
 */
@Schema(description = "버스 정류장 데이터")
data class BusStopResponse(
    @get:Schema(description = "아이디 PK", example = "ac130005-9cf0-14ae-819c-f08586140000")
    val id: UUID,

    @get:Schema(description = "정류소 번호 (STOPS_NO)", example = "11833")
    val stopId: String,

    @get:Schema(description = "정류소 이름 (STOPS_NM)", example = "중계역")
    val stopName: String,

    @get:JsonProperty("xCrd")
    @get:Schema(description = "경도 WGS84 (XCRD)", example = "127.0627647")
    val xCrd: String,

    @get:JsonProperty("yCrd")
    @get:Schema(description = "위도 WGS84 (YCRD)", example = "37.6472686")
    val yCrd: String,

    @get:Schema(description = "노드 ID (NODE_ID)", example = "110000669")
    val nodeId: String,

    @get:Schema(description = "정류소 유형 (STOPS_TYPE)", example = "가로변시간")
    val stopsType: String,

    @get:Schema(description = "생성일시", example = "2026-03-15 08:03:28.405709 +00:00")
    val createdAt: OffsetDateTime,

    @get:Schema(description = "수정일시", example = "2026-03-15 08:03:28.405709 +00:00")
    val updatedAt: OffsetDateTime,

    @get:Schema(
        description = "정류소 현재 단가. 단가 미등록 정류소는 null (0원과 구분됨)",
        example = "1500.00",
        nullable = true,
    )
    val unitPrice: BigDecimal? = null,
) {
    companion object {
        /**
         * StopEntity -> BusStopResponse 변환 팩토리.
         *
         * @param unitPrice 배치 조회한 단가. 미등록이면 null을 그대로 전달한다(0으로 대체 금지).
         */
        fun of(stop: StopEntity, unitPrice: BigDecimal? = null): BusStopResponse = BusStopResponse(
            id = stop.id.value,
            stopId = stop.stopId,
            stopName = stop.stopName,
            // DB는 NUMERIC이지만 기존 API 계약(문자열)을 유지한다.
            // toPlainString()으로 지수 표기(1.27E+2)가 섞이지 않도록 고정한다.
            xCrd = stop.xCrd.toPlainString(),
            yCrd = stop.yCrd.toPlainString(),
            nodeId = stop.nodeId,
            stopsType = stop.stopsType.name,
            createdAt = stop.createdAt,
            updatedAt = stop.updatedAt,
            unitPrice = unitPrice,
        )
    }
}
