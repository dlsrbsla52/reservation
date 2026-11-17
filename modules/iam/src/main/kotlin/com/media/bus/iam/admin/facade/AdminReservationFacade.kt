package com.media.bus.iam.admin.facade

import com.media.bus.common.web.wrapper.PageResult
import com.media.bus.iam.port.ReservationAdminPort
import com.media.bus.iam.port.dto.reservation.AdminReservationDetail
import com.media.bus.iam.port.dto.reservation.AdminReservationView
import com.media.bus.iam.port.dto.reservation.CompleteToContractRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import java.util.*

/**
 * ## 어드민 예약 처리 조율 Facade
 *
 * 설계 의도:
 * - 어드민 예약을 제어하기 위해 `ReservationAdminPort`(reservation 모듈)를 조율한다.
 * - 필요 시 회원 정보(memberService)와 예약 정보(reservationAdminPort)를 결합하여 반환한다.
 */
@Service
class AdminReservationFacade(
    private val reservationAdminPort: ReservationAdminPort,
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
        val rawPage = reservationAdminPort.searchAdminReservations(status, assigneeId, stopId, createdFrom, createdTo, page, size)
        
        log.debug("[AdminReservationFacade] 예약 목록 조회 완료: page={}, size={}, count={}", page, size, rawPage.items?.size ?: 0)
        return PageResult(
            items = rawPage.items ?: emptyList(),
            totalCnt = rawPage.totalCnt ?: 0L,
            pageRows = rawPage.pageRows ?: size,
            pageNum = rawPage.pageNum ?: page
        )
    }

    fun getReservationDetailForAdmin(reservationId: UUID): AdminReservationDetail {
        return reservationAdminPort.getReservationDetailForAdmin(reservationId)
    }

    fun assignReservation(reservationId: UUID, assigneeId: UUID) {
        reservationAdminPort.assignReservation(reservationId, assigneeId)
    }

    fun updateReservationStatus(reservationId: UUID, status: String, note: String?) {
        reservationAdminPort.updateReservationStatus(reservationId, status, note)
    }

    fun extendHold(reservationId: UUID, holdExpiresAt: OffsetDateTime) {
        reservationAdminPort.extendHold(reservationId, holdExpiresAt)
    }

    fun completeReservationToContract(reservationId: UUID, request: CompleteToContractRequest) {
        reservationAdminPort.completeReservationToContract(reservationId, request)
    }
}
