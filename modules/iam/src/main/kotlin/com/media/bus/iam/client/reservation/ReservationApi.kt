package com.media.bus.iam.client.reservation

import com.media.bus.iam.client.reservation.dto.AdminContractPageResponse
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.service.annotation.GetExchange
import org.springframework.web.service.annotation.HttpExchange
import java.util.*

/**
 * ## reservation 서비스 내부 API의 선언적 HTTP 인터페이스
 *
 * 설계 의도:
 * - `@HttpExchange` 기반 프록시가 HTTP 호출을 자동 처리하므로 RestClient 직접 조작 코드가 불필요하다.
 * - 비즈니스 로직은 포함하지 않는다. `ReservationServiceClient`가 위임받아 처리한다.
 *
 * 매핑되는 reservation 측 컨트롤러:
 * - `InternalContractController` (`/api/v1/internal/reservation/contract`)
 */
@HttpExchange(url = "/api/v1/internal/reservation")
interface ReservationApi {

    /**
     * 매니저(memberId) 기준 계약 목록을 페이지 단위로 조회한다.
     * reservation 측에서 stop 정보가 결합된 어드민 view 응답을 반환한다.
     */
    @GetExchange("/contract/member/{memberId}")
    fun getContractsByMember(
        @PathVariable memberId: UUID,
        @RequestParam("page") page: Int,
        @RequestParam("size") size: Int,
    ): AdminContractPageResponse?
}
