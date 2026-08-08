package com.media.bus.iam.client.stop.dto

import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.*

/**
 * ## stop 서비스 내부 API 응답에서 필요한 정류소 정보의 iam 내부 표현
 *
 * 모듈 경계 원칙: stop 모듈의 `BusStopResponse`를 직접 참조하지 않는다.
 * iam 비즈니스(정류소 단가 관리, 어드민 정류소 조회)에서 실제로 사용하는 필드만 선언한다.
 */
data class StopInfo(
    val id: UUID,
    val stopId: String,
    val stopName: String,
    val xCrd: String,
    val yCrd: String,
    val nodeId: String,
    val stopsType: String,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
    /** 정류소 현재 단가. 단가 미등록 정류소는 null (0원과 구분). 기본값은 구버전 stop 응답 호환용. */
    val unitPrice: BigDecimal? = null,
)
