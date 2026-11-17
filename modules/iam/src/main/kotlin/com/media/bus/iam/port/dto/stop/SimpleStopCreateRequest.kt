package com.media.bus.iam.port.dto.stop


/**
 * ## 정류소 단건 등록 요청 (`StopAdminPort` 입력)
 *
 * `stopsType`을 `String`으로 두는 이유: 모듈 경계상 stop의 `StopType` enum을 참조하지 않는다.
 * enum 변환은 `app` 어댑터가 하고, 알 수 없는 값이면 400이다. 나머지 필드 검증은 stop `StopService.createOneStop`이 한다.
 */
data class SimpleStopCreateRequest(
    val stopId: String,
    val stopName: String,
    val xCrd: String,
    val yCrd: String,
    val nodeId: String,
    val stopsType: String,
)
