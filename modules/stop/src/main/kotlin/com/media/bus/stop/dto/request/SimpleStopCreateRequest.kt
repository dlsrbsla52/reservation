package com.media.bus.stop.dto.request

import com.media.bus.stop.entity.enums.StopType
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

/**
 * ## 정류소 단건 수기 등록 요청 DTO
 */
data class SimpleStopCreateRequest(
    @field:NotNull
    @field:Size(max = 50)
    val stopId: String,

    @field:NotNull
    @field:Size(max = 50)
    val stopName: String,

    @field:NotNull
    @field:Size(max = 50)
    val xCrd: String,

    @field:NotNull
    @field:Size(max = 50)
    val yCrd: String,

    @field:NotNull
    @field:Size(max = 50)
    val nodeId: String,

    @field:NotNull
    val stopsType: StopType,
)
