package com.media.bus.stop.dto.external

import com.fasterxml.jackson.annotation.JsonProperty

/** 서울 열린데이터광장 busStopLocationXyInfo API 단건 행(row) DTO */
data class SeoulBusStopRow(
    @param:JsonProperty("STOPS_NO")
    val stopsNo: String,

    @param:JsonProperty("STOPS_NM")
    val stopsName: String,

    @param:JsonProperty("XCRD")
    val xCrd: String,

    @param:JsonProperty("YCRD")
    val yCrd: String,

    @param:JsonProperty("NODE_ID")
    val nodeId: String,

    @param:JsonProperty("STOPS_TYPE")
    val stopsType: String,
)
