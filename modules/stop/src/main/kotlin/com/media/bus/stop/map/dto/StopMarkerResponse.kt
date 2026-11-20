package com.media.bus.stop.map.dto

import com.media.bus.stop.entity.enums.StopType
import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.util.*

/** 지도 마커 전용 경량 응답 DTO */
@Schema(name = "StopMapMarkerResponse", description = "지도 마커용 정류소 좌표")
data class StopMarkerResponse(
    @get:Schema(description = "정류소 PK (UUID)", example = "ac130005-9cf0-14ae-819c-f08586140000")
    val id: UUID,

    @get:Schema(description = "정류소 번호 (STOPS_NO)", example = "11833")
    val stopId: String,

    @get:Schema(description = "정류소 이름", example = "중계역")
    val stopName: String,

    @get:Schema(description = "위도 WGS84 (y_crd)", example = "37.6472686")
    val lat: BigDecimal,

    @get:Schema(description = "경도 WGS84 (x_crd)", example = "127.0627647")
    val lng: BigDecimal,

    @get:Schema(description = "정류소 유형", example = "VILLAGE_BUS")
    val stopsType: StopType,
)
