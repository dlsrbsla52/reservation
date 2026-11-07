package com.media.bus.iam.admin.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.media.bus.iam.client.stop.dto.SimpleStopCreateRequest
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

@Schema(description = "정류소 단건 수기 등록 요청")
data class CreateSimpleStopRequest(
    @field:NotBlank
    @field:Size(max = 50)
    @param:Schema(description = "정류소 번호(ARS ID)", example = "12345")
    val stopId: String,

    @field:NotBlank
    @field:Size(max = 50)
    @param:Schema(description = "정류소 이름", example = "강남역")
    val stopName: String,

    // 게터가 getXCrd() 라 Jackson 기본 네이밍은 xcrd — 프론트·stop 모듈과 같은 xCrd 로 고정한다.
    @field:NotBlank
    @field:Size(max = 50)
    @get:JsonProperty("xCrd")
    @get:Schema(description = "경도 WGS84", example = "127.0627647")
    val xCrd: String,

    @field:NotBlank
    @field:Size(max = 50)
    @get:JsonProperty("yCrd")
    @get:Schema(description = "위도 WGS84", example = "37.6472686")
    val yCrd: String,

    @field:NotBlank
    @field:Size(max = 50)
    @param:Schema(description = "노드 ID", example = "110000669")
    val nodeId: String,

    // stop 모듈의 StopType enum 은 모듈 경계상 참조하지 않는다. 검증은 stop 모듈이 수행한다.
    @field:NotBlank
    @param:Schema(description = "정류소 유형", example = "ROADSIDE_TIMED")
    val stopsType: String,
) {
    /** stop S2S 요청 DTO로 변환한다. */
    fun toClientRequest() = SimpleStopCreateRequest(
        stopId = stopId,
        stopName = stopName,
        xCrd = xCrd,
        yCrd = yCrd,
        nodeId = nodeId,
        stopsType = stopsType,
    )
}
