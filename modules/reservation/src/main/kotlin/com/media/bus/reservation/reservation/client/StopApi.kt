package com.media.bus.reservation.reservation.client

import com.media.bus.reservation.reservation.dto.response.internal.StopPageResponse
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.service.annotation.GetExchange
import org.springframework.web.service.annotation.HttpExchange
import java.util.*

/**
 * ## stop 서비스 내부 API의 선언적 HTTP 인터페이스
 *
 * 설계 의도:
 * - `@HttpExchange` 기반 프록시가 HTTP 호출을 자동 처리하므로 RestClient 직접 조작 코드가 불필요하다.
 * - 비즈니스 로직(null 체크, 리스트 변환)은 포함하지 않는다.
 *   `StopServiceClient`가 이 인터페이스를 위임받아 비즈니스 로직을 처리한다.
 */
@HttpExchange(url = "/api/v1/internal/stop")
interface StopApi {

    /** pk(UUID) 기준 정류소 조회 */
    @GetExchange
    fun getStopByPk(@RequestParam("pk") pk: UUID): StopPageResponse?

    /** stopId(정류소 번호) 기준 정류소 조회 */
    @GetExchange
    fun getStopByStopId(@RequestParam("stopId") stopId: String): StopPageResponse?
}
