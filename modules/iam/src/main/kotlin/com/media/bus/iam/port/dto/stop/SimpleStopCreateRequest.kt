package com.media.bus.iam.port.dto.stop

/** 정류소 단건 등록 요청 (`StopAdminPort` 입력) */
data class SimpleStopCreateRequest(
    val stopId: String,
    val stopName: String,
    val xCrd: String,
    val yCrd: String,
    val nodeId: String,
    val stopsType: String,
)
