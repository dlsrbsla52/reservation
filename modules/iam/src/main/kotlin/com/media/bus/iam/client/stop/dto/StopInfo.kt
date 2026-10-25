package com.media.bus.iam.client.stop.dto

import com.fasterxml.jackson.annotation.JsonAlias
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
    /**
     * 경도 WGS84.
     *
     * `@JsonAlias("xcrd")` 는 **롤링 배포 호환용**이다.
     * stop 모듈이 좌표 직렬화명을 `xcrd`(Jackson 기본 네이밍이 `getXCrd()` 에서 유도한 이름) 에서
     * `xCrd` 로 고정하기 전까지 구버전 인스턴스는 `xcrd` 를 보낸다.
     * 별칭이 없으면 배포가 완전히 끝날 때까지 정류소 조회가 `KotlinInvalidNullException` 으로 실패한다.
     *
     * 구버전 stop 인스턴스가 모두 교체된 뒤에는 제거해도 된다.
     */
    @JsonAlias("xcrd")
    val xCrd: String,

    /** 위도 WGS84. 별칭 사유는 [xCrd] 와 동일하다. */
    @JsonAlias("ycrd")
    val yCrd: String,
    val nodeId: String,
    val stopsType: String,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
    /** 정류소 현재 단가. 단가 미등록 정류소는 null (0원과 구분). 기본값은 구버전 stop 응답 호환용. */
    val unitPrice: BigDecimal? = null,
)
