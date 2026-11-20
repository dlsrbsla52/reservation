package com.media.bus.reservation.reservation.service

import com.media.bus.common.exceptions.BaseException
import com.media.bus.common.exceptions.BusinessException
import com.media.bus.common.result.Result
import com.media.bus.common.result.type.CommonResult
import com.media.bus.common.validation.InputValidator
import com.media.bus.common.web.wrapper.PageResult
import com.media.bus.contract.security.MemberPrincipal
import com.media.bus.reservation.reservation.dto.request.CompleteToContractRequest
import com.media.bus.reservation.reservation.dto.request.CreateStopReservationRequest
import com.media.bus.reservation.reservation.dto.response.AdminReservationListResponse
import com.media.bus.reservation.reservation.dto.response.CreateReservationResponse
import com.media.bus.reservation.reservation.dto.response.MyReservationResponse
import com.media.bus.reservation.reservation.dto.response.ReservationDetailResponse
import com.media.bus.reservation.reservation.entity.enums.ReservationStatus
import com.media.bus.reservation.reservation.hold.StopHoldGate
import com.media.bus.reservation.reservation.result.ReservationResult
import com.media.bus.reservation.slot.StopSlot
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import java.util.*

/** 예약 흐름을 조율하는 Facade */
@Service
class ReservationFacade(
    private val stopResolutionService: StopResolutionService,
    private val reservationService: ReservationService,
    private val stopHoldGate: StopHoldGate,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 요청한 정류소마다 선착순 선점을 시도하고 정류소별 결과를 돌려준다. */
    fun createReservation(principal: MemberPrincipal, request: CreateStopReservationRequest): CreateReservationResponse =
        CreateReservationResponse.of(request.stopId.map { reserveOne(principal.id, it, request) })

    private fun reserveOne(
        memberId: UUID,
        stopId: UUID,
        request: CreateStopReservationRequest,
    ): CreateReservationResponse.Item {
        val slotNo = StopSlot.DEFAULT_NO

        val pass = stopHoldGate.tryEnter(stopId, slotNo)
            ?: return failure(stopId, ReservationResult.STOP_ALREADY_HELD)

        return try {
            val stop = stopResolutionService.resolveStop(stopId)
            val reservation = reservationService.createReservation(memberId, stop, request, slotNo)
            stopHoldGate.confirm(pass)
            CreateReservationResponse.Item(
                stopId = stopId,
                success = true,
                reservationId = reservation.id.value,
                holdExpiresAt = reservation.holdExpiresAt,
            )
        } catch (e: BaseException) {
            if (e.result in OCCUPIED_RESULTS) {
                stopHoldGate.confirm(pass)
            } else {
                stopHoldGate.leave(pass)
            }
            log.debug("[ReservationFacade] 예약 실패: stopId={}, memberId={}, code={}", stopId, memberId, e.result.code)
            failure(stopId, e.result, e.message)
        } catch (e: Exception) {
            stopHoldGate.leave(pass)
            log.error("[ReservationFacade] 예약 처리 중 예기치 못한 오류: stopId={}, memberId={}", stopId, memberId, e)
            failure(stopId, CommonResult.ERROR)
        }
    }

    private fun failure(stopId: UUID, result: Result, message: String = result.message) =
        CreateReservationResponse.Item(stopId = stopId, success = false, errorCode = result.code, message = message)

    /** 예약을 취소하고, 커밋 후 풀린 광고면의 점유 캐시를 지운다. */
    fun cancelReservation(principal: MemberPrincipal, reservationId: UUID) {
        stopHoldGate.evict(reservationService.cancelReservation(principal.id, reservationId))
    }

    /** 내 예약 목록을 조회하고 각 항목에 정류소 정보를 결합하여 반환한다. */
    fun getMyReservations(principal: MemberPrincipal, page: Int, size: Int): PageResult<MyReservationResponse> =
        enrichWithStops(reservationService.getMyReservations(principal.id, page, size))

    /** 특정 회원의 예약 목록을 조회한다(어드민 전용). */
    fun getMemberReservations(memberId: UUID, page: Int, size: Int): PageResult<MyReservationResponse> =
        enrichWithStops(reservationService.getMyReservations(memberId, page, size))

    /** 예약 목록 페이지의 각 row에 정류소 정보(stopNumber/stopName)를 결합한다. */
    private fun enrichWithStops(raw: PageResult<MyReservationResponse>): PageResult<MyReservationResponse> {
        val stopIds = raw.items.map { it.stopId }.toSet()
        val stopMap = stopResolutionService.resolveStops(stopIds)
        val enriched = raw.items.map { row ->
            val info = stopMap[row.stopId]
            row.copy(stopNumber = info?.stopId, stopName = info?.stopName)
        }
        return PageResult(
            items = enriched,
            totalCnt = raw.totalCnt,
            pageRows = raw.pageRows,
            pageNum = raw.pageNum,
        )
    }

    /** 예약 단건 상세에 정류소 정보를 결합하여 반환한다. */
    fun getReservationDetail(principal: MemberPrincipal, reservationId: UUID): ReservationDetailResponse {
        val raw = reservationService.getReservationDetail(principal.id, reservationId)
        val info = stopResolutionService.resolveStops(setOf(raw.stopId))[raw.stopId]
        return raw.copy(stopNumber = info?.stopId, stopName = info?.stopName)
    }

    /** 어드민용 전체 예약 목록 검색 및 정류소 정보 일괄 결합 페이징 조회. */
    fun searchAdminReservations(
        status: ReservationStatus?,
        assigneeId: UUID?,
        stopId: UUID?,
        createdFrom: OffsetDateTime?,
        createdTo: OffsetDateTime?,
        rawPage: Int,
        rawSize: Int
    ): PageResult<AdminReservationListResponse> {
        val page = rawPage.coerceAtLeast(0)
        val size = rawSize.coerceIn(1, ADMIN_MAX_PAGE_SIZE)
        val (reservations, total) = reservationService.searchAdminReservations(status, assigneeId, stopId, createdFrom, createdTo, page, size)

        val stopIds = reservations.map { it.stopId }.toSet()
        val stopMap = stopResolutionService.resolveStops(stopIds)

        val items = reservations.map { reservation ->
            val info = stopMap[reservation.stopId]
            AdminReservationListResponse.of(reservation, info?.stopId, info?.stopName)
        }

        return PageResult(
            items = items,
            totalCnt = total,
            pageRows = size,
            pageNum = page
        )
    }

    /** 예약 담당자 지정 */
    fun assignReservation(reservationId: UUID, assigneeId: UUID) {
        reservationService.assignReservation(reservationId, assigneeId)
    }

    /** 예약 상태 변경 및 상담 기록 추가. */
    fun updateReservationStatus(reservationId: UUID, targetStatus: ReservationStatus, note: String?) {
        if (note != null && note.length > MAX_NOTE_LENGTH) throw BusinessException(CommonResult.VALIDATION_FAIL)
        reservationService.updateReservationStatus(reservationId, targetStatus, note)?.let(stopHoldGate::evict)
    }

    /** 선점 기간 연장 (어드민용) */
    fun extendHold(reservationId: UUID, holdExpiresAt: OffsetDateTime) {
        reservationService.extendHold(reservationId, holdExpiresAt)
    }

    /** 선점 기간이 지난 예약을 만료시키고 풀린 광고면의 점유 캐시를 지운다. */
    fun expireOverdueHolds(): Int {
        val released = reservationService.expireOverdueHolds()
        released.forEach(stopHoldGate::evict)
        return released.size
    }

    /** 예약 상세 및 상담 전체 이력 조회 (어드민용) */
    fun getReservationDetailForAdmin(reservationId: UUID): ReservationDetailResponse {
        val raw = reservationService.getReservationDetailForAdmin(reservationId)
        val info = stopResolutionService.resolveStops(setOf(raw.stopId))[raw.stopId]
        return raw.copy(stopNumber = info?.stopId, stopName = info?.stopName)
    }

    /** 예약을 완료 처리하고 해당 예약건을 기반으로 즉시 광고 계약을 생성한다. */
    fun completeReservationToContract(reservationId: UUID, request: CompleteToContractRequest) {
        InputValidator.validate(request)
        val detail = reservationService.getReservationDetailForAdmin(reservationId)
        val stopInfo = stopResolutionService.resolveStop(detail.stopId)

        val released = reservationService.completeToContract(reservationId, stopInfo, request)
        stopHoldGate.evict(released)
    }

    private companion object {
        const val ADMIN_MAX_PAGE_SIZE = 100

        const val MAX_NOTE_LENGTH = 1000

        val OCCUPIED_RESULTS: Set<Result> =
            setOf(ReservationResult.STOP_ALREADY_HELD, ReservationResult.RESERVATION_ALREADY_EXISTS)
    }
}
