package com.media.bus.reservation.reservation.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

@Schema(description = "회원의 예약 요청 확인을 위한 DTO")
data class CreateStopReservationRequest(
    // 정류소마다 S2S 호출과 트랜잭션이 하나씩 돌기 때문에 한 요청의 크기를 제한한다.
    @field:NotEmpty
    @field:Size(max = MAX_STOPS_PER_REQUEST)
    @param:Schema(description = "요청 stopId (한 번에 최대 100개)")
    val stopId: Set<UUID>,

    @param:Schema(description = "상담 요청 일자")
    val consultationRequestedAt: OffsetDateTime,

    @param:Schema(description = "계약 시작 희망 일자")
    val desiredContractStartDate: LocalDate?,
) {
    companion object {
        const val MAX_STOPS_PER_REQUEST = 100
    }
}
