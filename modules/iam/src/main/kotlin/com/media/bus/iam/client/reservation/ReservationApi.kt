package com.media.bus.iam.client.reservation

import com.media.bus.common.web.response.ApiResponse
import com.media.bus.iam.client.reservation.dto.*
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.service.annotation.GetExchange
import org.springframework.web.service.annotation.HttpExchange
import org.springframework.web.service.annotation.PostExchange
import org.springframework.web.service.annotation.PutExchange
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
 * - `InternalReservationController` (`/api/v1/internal/reservation`)
 */
@HttpExchange(url = "/api/v1/internal/reservation")
interface ReservationApi {

    @GetExchange("/contract/member/{memberId}")
    fun getContractsByMember(
        @PathVariable memberId: UUID,
        @RequestParam("page") page: Int,
        @RequestParam("size") size: Int,
    ): AdminContractPageResponse?

    @GetExchange("/reservations")
    fun searchAdminReservations(
        @RequestParam("status") status: String?,
        @RequestParam("assigneeId") assigneeId: UUID?,
        @RequestParam("stopId") stopId: UUID?,
        @RequestParam("createdFrom") createdFrom: String?,
        @RequestParam("createdTo") createdTo: String?,
        @RequestParam("page") page: Int,
        @RequestParam("size") size: Int,
    ): AdminReservationPageResponse?

    @GetExchange("/reservations/{reservationId}")
    fun getReservationDetailForAdmin(
        @PathVariable reservationId: UUID,
    ): AdminReservationDetailResponse?

    @PutExchange("/reservations/{reservationId}/assignee")
    fun assignReservation(
        @PathVariable reservationId: UUID,
        @RequestBody request: AssignReservationRequest,
    ): ApiResponse<Unit?>?

    @PutExchange("/reservations/{reservationId}/status")
    fun updateReservationStatus(
        @PathVariable reservationId: UUID,
        @RequestBody request: UpdateReservationStatusRequest,
    ): ApiResponse<Unit?>?

    @PostExchange("/reservations/{reservationId}/complete-to-contract")
    fun completeReservationToContract(
        @PathVariable reservationId: UUID,
        @RequestBody request: CompleteToContractRequest,
    ): ApiResponse<Unit?>?
}
