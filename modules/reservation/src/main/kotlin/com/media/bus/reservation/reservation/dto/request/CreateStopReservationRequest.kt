package com.media.bus.reservation.reservation.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

@Schema(description = "회원의 예약 요청 확인을 위한 DTO")
data class CreateStopReservationRequest(
    // 정류소마다 정류소 조회와 트랜잭션이 하나씩 돌기 때문에 한 요청의 크기를 제한한다.
    @field:NotEmpty
    @field:Size(max = MAX_STOPS_PER_REQUEST)
    @param:Schema(description = "요청 stopId (한 번에 최대 100개)")
    val stopId: Set<UUID>,

    @param:Schema(description = "상담 요청 일자")
    val consultationRequestedAt: OffsetDateTime,

    // 필수. 컬럼이 처음 설계(V001)부터 NOT NULL 이다. 값이 없으면 Kotlin 생성자 null 검사가 실패해 MVC 가 400/00260 으로 응답한다.
    @param:Schema(description = "계약 시작 희망 일자")
    val desiredContractStartDate: LocalDate,
) {
    init {
        // MVC 의 Jackson 은 Kotlin 의 원소 nullability 를 모르므로 [""]·[null] 이 null 원소로 들어온다(타입은 Set<UUID> 인데도).
        // 원소 위치의 @NotNull 은 Kotlin 이 바이트코드에 남기지 않아 Bean Validation 으로도 못 막는다.
        // 여기서 막지 않으면 Facade 에서 NPE(500)가 난다. 생성자에서 던지면 MVC 가 본문 해석 실패(400/00260)로 응답한다.
        val ids: Collection<UUID?> = stopId
        require(ids.none { it == null }) { "stopId 에 빈 값이 있습니다." }
    }

    companion object {
        const val MAX_STOPS_PER_REQUEST = 100
    }
}
