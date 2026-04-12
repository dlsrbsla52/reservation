package com.media.bus.reservation.reservation.dto.request

import java.util.*

/**
 * ## Stop 서비스의 일괄 조회 요청 DTO (reservation 내부 사본)
 *
 * 모듈 경계 원칙에 따라 stop 모듈의 DTO를 직접 참조하지 않는다.
 * 필드명·JSON 구조는 stop 측 `BulkStopLookupRequest`와 반드시 일치해야 한다.
 */
data class BulkStopLookupRequest(
    val ids: List<UUID>,
)
