package com.media.bus.stop.dto.external

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * ## 서울 열린데이터광장 busStopLocationXyInfo API 단건 행(row) DTO
 *
 * 참고: `http://openapi.seoul.go.kr:8088/{serviceKey}/json/busStopLocationXyInfo/{start}/{end}/`
 */
data class SeoulBusStopRow(
    @JsonProperty("STOPS_NO")
    val stopsNo: String,

    @JsonProperty("STOPS_NM")
    val stopsName: String,

    @JsonProperty("XCRD")
    val xCrd: String,

    @JsonProperty("YCRD")
    val yCrd: String,

    @JsonProperty("NODE_ID")
    val nodeId: String,

    @JsonProperty("STOPS_TYPE")
    val stopsType: String,
)
