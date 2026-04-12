package com.media.bus.stop.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import java.util.*

/**
 * ## 정류소 일괄 조회 요청 DTO (내부 전용)
 *
 * 예약 서비스가 예약 목록의 각 `stopId`를 한 번에 조회할 때 사용한다.
 * 대량 조회로 Stop 서비스를 과부하시키지 않도록 상한을 둔다.
 */
@Schema(description = "정류소 일괄 조회 요청")
data class BulkStopLookupRequest(

    @param:Schema(description = "정류소 pk(UUID) 목록")
    @field:NotEmpty(message = "조회할 정류소 ID를 1개 이상 입력해주세요.")
    @field:Size(max = 200, message = "한 번에 조회할 수 있는 정류소는 최대 200개입니다.")
    val ids: List<UUID>,
)
