package com.media.bus.reservation.reservation.dto.response.internal

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.media.bus.reservation.reservation.dto.response.StopInfo

/**
 * ## stop 서비스 내부 API의 페이지 응답 역직렬화용 DTO
 *
 * 설계 의도:
 * - stop 모듈 클래스를 직접 참조하지 않기 위해 reservation 내부 전용으로 선언한다.
 * - 알 수 없는 필드는 무시하여 stop API 응답 변경에 유연하게 대응한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class StopPageResponse(val data: StopPageData?) {

    /** stop 내부 API 응답의 data 필드 구조 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class StopPageData(val list: List<StopInfo>?)
}
