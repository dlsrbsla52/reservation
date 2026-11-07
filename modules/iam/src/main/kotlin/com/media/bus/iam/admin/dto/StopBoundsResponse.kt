package com.media.bus.iam.admin.dto

import com.media.bus.iam.client.stop.dto.StopBoundsApiResponse
import com.media.bus.iam.client.stop.dto.StopMarkerInfo
import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.util.*

@Schema(description = "지도 영역 내 정류소 마커 목록")
data class StopBoundsResponse(
    @param:Schema(description = "영역에 포함된 정류소 마커 목록")
    val items: List<StopMarkerResponse>,
    @param:Schema(description = "상한 초과로 결과가 잘렸는지 여부", example = "false")
    val truncated: Boolean,
    @param:Schema(description = "적용된 반환 상한 건수", example = "1000")
    val limit: Int,
) {
    companion object {
        fun of(info: StopBoundsApiResponse.StopBoundsInfo) = StopBoundsResponse(
            items = info.items.orEmpty().map(StopMarkerResponse::of),
            truncated = info.truncated ?: false,
            limit = info.limit ?: 0,
        )
    }
}

@Schema(description = "지도 마커용 정류소 좌표")
data class StopMarkerResponse(
    @param:Schema(description = "정류소 pk(UUID)")
    val id: UUID,
    @param:Schema(description = "정류소 번호", example = "11833")
    val stopId: String,
    @param:Schema(description = "정류소 이름", example = "중계역")
    val stopName: String,
    @param:Schema(description = "위도 WGS84", example = "37.6472686")
    val lat: BigDecimal,
    @param:Schema(description = "경도 WGS84", example = "127.0627647")
    val lng: BigDecimal,
    @param:Schema(description = "정류소 유형", example = "VILLAGE_BUS")
    val stopsType: String,
) {
    companion object {
        fun of(info: StopMarkerInfo) = StopMarkerResponse(
            id = info.id,
            stopId = info.stopId,
            stopName = info.stopName,
            lat = info.lat,
            lng = info.lng,
            stopsType = info.stopsType,
        )
    }
}
