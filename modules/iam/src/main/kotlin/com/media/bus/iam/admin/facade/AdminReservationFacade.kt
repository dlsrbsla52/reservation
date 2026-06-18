package com.media.bus.iam.admin.facade

import com.media.bus.common.web.wrapper.PageResult
import com.media.bus.iam.client.reservation.ReservationServiceClient
import com.media.bus.iam.client.reservation.dto.AdminReservationDetailResponse
import com.media.bus.iam.client.reservation.dto.AdminReservationView
import com.media.bus.iam.client.reservation.dto.CompleteToContractRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.*

/**
 * ## 어드민 예약 처리 조율 Facade
 *
 * 설계 의도:
 * - 어드민 예약을 제어하기 위해 reservation S2S 클라이언트를 조율한다.
 * - 필요 시 회원 정보(memberService)와 예약 정보(reservationServiceClient)를 결합하여 반환한다.
 */
@Service
class AdminReservationFacade(
    private val reservationServiceClient: ReservationServiceClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun searchAdminReservations(
        status: String?,
        assigneeId: UUID?,
        stopId: UUID?,
        createdFrom: String?,
        createdTo: String?,
        page: Int,
        size: Int
    ): PageResult<AdminReservationView> {
        val rawPage = reservationServiceClient.searchAdminReservations(status, assigneeId, stopId, createdFrom, createdTo, page, size)
        
        log.debug("[AdminReservationFacade] 예약 목록 조회 완료: page={}, size={}, count={}", page, size, rawPage.items?.size ?: 0)
        return PageResult(
            items = rawPage.items ?: emptyList(),
            totalCnt = rawPage.totalCnt ?: 0L,
            pageRows = rawPage.pageRows ?: size,
            pageNum = rawPage.pageNum ?: page
        )
    }

    fun getReservationDetailForAdmin(reservationId: UUID): AdminReservationDetailResponse.AdminReservationDetail {
        return reservationServiceClient.getReservationDetailForAdmin(reservationId)
    }

    fun assignReservation(reservationId: UUID, assigneeId: UUID) {
        reservationServiceClient.assignReservation(reservationId, assigneeId)
    }

    fun updateReservationStatus(reservationId: UUID, status: String, note: String?) {
        reservationServiceClient.updateReservationStatus(reservationId, status, note)
    }

    fun completeReservationToContract(reservationId: UUID, request: CompleteToContractRequest) {
        reservationServiceClient.completeReservationToContract(reservationId, request)
    }
}
