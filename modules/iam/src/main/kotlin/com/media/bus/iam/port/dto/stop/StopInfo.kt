package com.media.bus.iam.port.dto.stop

import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.*

/**
 * ## 정류소 정보 (`StopAdminPort` 응답)
 *
 * 모듈 경계 원칙: stop 모듈의 `BusStopResponse`를 직접 참조하지 않는다.
 * iam 비즈니스(정류소 단가 관리, 어드민 정류소 조회)에서 실제로 사용하는 필드만 선언한다.
 * 프런트에는 `admin.dto.StopResponse`로 바꿔서 내보낸다.
 *
 * 모든 필드에 기본값을 두지 않는다. 예전 JSON 역직렬화는 이름이 같은 필드를 자동으로 옮겼지만, 지금은 `app` 어댑터가
 * 필드별로 옮기므로 기본값이 있으면 빠뜨려도 컴파일된다(예전 `unitPrice = null` 기본값이 그 위험이었다).
 */
data class StopInfo(
    val id: UUID,
    val stopId: String,
    val stopName: String,
    /** 경도 WGS84. */
    val xCrd: String,
    /** 위도 WGS84. */
    val yCrd: String,
    val nodeId: String,
    val stopsType: String,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
    /** 정류소 현재 단가. 단가 미등록 정류소는 null (0원과 구분). */
    val unitPrice: BigDecimal?,
)
