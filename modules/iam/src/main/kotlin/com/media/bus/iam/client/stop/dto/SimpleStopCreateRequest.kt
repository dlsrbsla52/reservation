package com.media.bus.iam.client.stop.dto

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * ## stop 서비스 정류소 단건 등록 S2S 요청 DTO
 *
 * - `stopsType`을 `String`으로 두는 이유: 모듈 경계상 stop의 `StopType` enum을 참조하지 않는다.
 *   유효성 검증은 stop 모듈이 수행한다(알 수 없는 값이면 400).
 * - `xCrd`/`yCrd`는 게터가 `getXCrd()`라 Jackson 기본 네이밍이 `xcrd`로 직렬화한다.
 *   stop 측은 `xCrd`로 역직렬화하므로 `@get:JsonProperty`로 이름을 고정한다.
 */
data class SimpleStopCreateRequest(
    val stopId: String,
    val stopName: String,
    @get:JsonProperty("xCrd")
    val xCrd: String,
    @get:JsonProperty("yCrd")
    val yCrd: String,
    val nodeId: String,
    val stopsType: String,
)
