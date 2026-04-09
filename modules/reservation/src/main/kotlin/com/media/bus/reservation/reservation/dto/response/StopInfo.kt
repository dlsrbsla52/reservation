package com.media.bus.reservation.reservation.dto.response

import java.util.*

/**
 * ## stop 서비스 내부 API 응답에서 필요한 정류소 정보의 reservation 내부 표현
 *
 * 모듈 경계 원칙: stop 모듈의 `BusStopResponse`를 직접 참조하지 않는다.
 * reservation 비즈니스에서 실제로 사용하는 필드만 선언한다.
 */
data class StopInfo(
    val id: UUID,
    val stopId: String,
    val stopName: String,
)
