package com.media.bus.reservation.reservation.service

import com.media.bus.common.exceptions.BusinessException
import com.media.bus.common.web.wrapper.PageResult
import com.media.bus.reservation.contract.dto.request.CreateContractRequest
import com.media.bus.reservation.contract.service.ContractService
import com.media.bus.reservation.reservation.dto.request.CompleteToContractRequest
import com.media.bus.reservation.reservation.dto.request.CreateStopReservationRequest
import com.media.bus.reservation.reservation.dto.response.MyReservationResponse
import com.media.bus.reservation.reservation.dto.response.ReservationDetailResponse
import com.media.bus.reservation.reservation.dto.response.StopInfo
import com.media.bus.reservation.reservation.entity.ReservationConsultationEntity
import com.media.bus.reservation.reservation.entity.ReservationEntity
import com.media.bus.reservation.reservation.entity.enums.ReservationStatus
import com.media.bus.reservation.reservation.hold.HoldProperties
import com.media.bus.reservation.reservation.hold.SlotRef
import com.media.bus.reservation.reservation.repository.ReservationConsultationRepository
import com.media.bus.reservation.reservation.repository.ReservationRepository
import com.media.bus.reservation.reservation.result.ReservationResult
import com.media.bus.reservation.slot.StopSlot
import com.media.bus.reservation.support.SQLSTATE_UNIQUE_VIOLATION
import com.media.bus.reservation.support.flushTranslating
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.*

/** 예약 도메인의 DB 저장/조회를 담당하는 서비스 */
@Service
class ReservationService(
    private val reservationRepository: ReservationRepository,
    private val consultationRepository: ReservationConsultationRepository,
    private val contractService: ContractService,
    private val holdProperties: HoldProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private val KST: ZoneId = ZoneId.of("Asia/Seoul")
    }

    private fun now(): OffsetDateTime = OffsetDateTime.now(KST)

    /** 예약 + 초기 상담(PENDING) row 를 생성하고 광고면을 선점한다. */
    @Transactional
    fun createReservation(
        memberId: UUID,
        stopInfo: StopInfo,
        request: CreateStopReservationRequest,
        slotNo: Short = StopSlot.DEFAULT_NO,
    ): ReservationEntity {
        val now = now()

        if (reservationRepository.countHoldingByMember(memberId) >= holdProperties.maxActivePerMember) {
            throw BusinessException(ReservationResult.RESERVATION_LIMIT_EXCEEDED)
        }

        val desiredStart = request.desiredContractStartDate.atStartOfDay(KST).toOffsetDateTime()
        if (contractService.isSlotOccupiedAt(stopInfo.id, slotNo, desiredStart)) {
            throw BusinessException(ReservationResult.STOP_UNDER_CONTRACT)
        }

        reservationRepository.findHolderForUpdate(stopInfo.id, slotNo)?.let { holder ->
            if (!holder.isHoldExpired(now)) {
                throw BusinessException(
                    if (holder.memberId == memberId) ReservationResult.RESERVATION_ALREADY_EXISTS
                    else ReservationResult.STOP_ALREADY_HELD,
                )
            }
            expire(holder, now)
            holder.flush()
        }

        val reservation = ReservationEntity.create(
            memberId = memberId,
            stopId = stopInfo.id,
            slotNo = slotNo,
            consultationRequestedAt = request.consultationRequestedAt,
            desiredContractStartDate = request.desiredContractStartDate,
            holdExpiresAt = now.plus(holdProperties.duration),
        )
        ReservationConsultationEntity.create(reservation, ReservationStatus.PENDING)

        flushTranslating(SQLSTATE_UNIQUE_VIOLATION) {
            BusinessException(ReservationResult.STOP_ALREADY_HELD)
        }

        log.debug(
            "[ReservationService] 예약 저장 완료: reservationId={}, memberId={}, stopId={}, holdExpiresAt={}",
            reservation.id.value, memberId, stopInfo.id, reservation.holdExpiresAt,
        )
        return reservation
    }

    /** 본인 예약 목록 조회 (페이지네이션). */
    @Transactional(readOnly = true)
    fun getMyReservations(memberId: UUID, page: Int, size: Int): PageResult<MyReservationResponse> {
        val reservations = reservationRepository.findByMemberIdPaged(memberId, page, size)
        val totalCnt = reservationRepository.countByMemberId(memberId)

        return PageResult(
            items = reservations.map { MyReservationResponse.of(it) },
            totalCnt = totalCnt,
            pageRows = size,
            pageNum = page,
        )
    }

    /** 본인 예약 단건 상세 조회. */
    @Transactional(readOnly = true)
    fun getReservationDetail(memberId: UUID, reservationId: UUID): ReservationDetailResponse {
        val reservation = reservationRepository.findById(reservationId)
            ?: throw BusinessException(ReservationResult.RESERVATION_NOT_FOUND)

        if (reservation.memberId != memberId) {
            throw BusinessException(ReservationResult.RESERVATION_ACCESS_DENIED)
        }

        return ReservationDetailResponse.of(reservation, consultationRepository.findAllByReservation(reservation))
    }

    /** 예약 취소 (soft). */
    @Transactional
    fun cancelReservation(memberId: UUID, reservationId: UUID): SlotRef {
        val reservation = reservationRepository.findByIdAndMemberIdForUpdate(reservationId, memberId)
            ?: throw BusinessException(ReservationResult.RESERVATION_NOT_FOUND)

        if (!reservation.status.canTransitionTo(ReservationStatus.CANCELLED)) {
            throw BusinessException(ReservationResult.RESERVATION_NOT_CANCELLABLE)
        }

        transition(reservation, ReservationStatus.CANCELLED, note = null, now = now())
        log.debug(
            "[ReservationService] 예약 취소 처리: reservationId={}, memberId={}",
            reservation.id.value, memberId,
        )
        return reservation.slot
    }

    /** 어드민용 전체 예약 목록 검색 및 조건부 필터링 페이징 조회. */
    @Transactional(readOnly = true)
    fun searchAdminReservations(
        status: ReservationStatus?,
        assigneeId: UUID?,
        stopId: UUID?,
        createdFrom: OffsetDateTime?,
        createdTo: OffsetDateTime?,
        page: Int,
        size: Int,
    ): Pair<List<ReservationEntity>, Long> {
        val list = reservationRepository.searchAdminReservations(status, assigneeId, stopId, createdFrom, createdTo, page, size)
        val total = reservationRepository.countAdminReservations(status, assigneeId, stopId, createdFrom, createdTo)
        return list to total
    }

    /** 예약 담당 어드민 배정/변경 */
    @Transactional
    fun assignReservation(reservationId: UUID, assigneeId: UUID) {
        val reservation = reservationRepository.findByIdForUpdate(reservationId)
            ?: throw BusinessException(ReservationResult.RESERVATION_NOT_FOUND)

        reservation.assigneeId = assigneeId
        log.info("[ReservationService] 예약 담당자 배정 완료: reservationId={}, assigneeId={}", reservationId, assigneeId)
    }

    /** 예약 상태 변경 및 상담 이력(메모) 기록. */
    @Transactional
    fun updateReservationStatus(reservationId: UUID, targetStatus: ReservationStatus, note: String?): SlotRef? {
        val reservation = reservationRepository.findByIdForUpdate(reservationId)
            ?: throw BusinessException(ReservationResult.RESERVATION_NOT_FOUND)

        val fromStatus = reservation.status
        if (targetStatus == ReservationStatus.EXPIRED || !fromStatus.canTransitionTo(targetStatus)) {
            throw BusinessException(ReservationResult.RESERVATION_INVALID_STATE_TRANSITION)
        }

        transition(reservation, targetStatus, note, now())
        log.info(
            "[ReservationService] 예약 상태 변경 완료: reservationId={}, fromStatus={}, toStatus={}",
            reservationId, fromStatus, targetStatus,
        )
        return reservation.slot.takeUnless { targetStatus.holdsSlot }
    }

    /** 예약을 완료(COMPLETED) 처리하고 같은 트랜잭션에서 계약을 생성한다. */
    @Transactional
    fun completeToContract(
        reservationId: UUID,
        stopInfo: StopInfo,
        request: CompleteToContractRequest,
    ): SlotRef {
        val reservation = reservationRepository.findByIdForUpdate(reservationId)
            ?: throw BusinessException(ReservationResult.RESERVATION_NOT_FOUND)

        if (!reservation.status.canTransitionTo(ReservationStatus.COMPLETED)) {
            throw BusinessException(ReservationResult.RESERVATION_INVALID_STATE_TRANSITION)
        }

        transition(reservation, ReservationStatus.COMPLETED, request.note, now())
        reservation.flush()

        val contractRequest = CreateContractRequest(
            stopId = reservation.stopId,
            contractName = request.contractName,
            totalAmount = request.totalAmount,
            payAmount = request.payAmount,
            paymentCycle = request.paymentCycle,
            paymentMethod = request.paymentMethod,
            memberId = reservation.memberId,
            note = request.note,
            contractStartDate = request.contractStartDate,
            contractEndDate = request.contractEndDate,
        )
        contractService.createContract(stopInfo, contractRequest, reservation.slotNo)

        log.info("[ReservationService] 예약 완료 및 계약 생성: reservationId={}, stopId={}", reservationId, stopInfo.id)
        return reservation.slot
    }

    /** 선점 만료 일시를 바꾼다(관리자 연장). */
    @Transactional
    fun extendHold(reservationId: UUID, holdExpiresAt: OffsetDateTime) {
        val now = now()
        if (!holdExpiresAt.isAfter(now)) {
            throw BusinessException(ReservationResult.RESERVATION_HOLD_INVALID_EXPIRY)
        }
        val reservation = reservationRepository.findByIdForUpdate(reservationId)
            ?: throw BusinessException(ReservationResult.RESERVATION_NOT_FOUND)

        if (!reservation.status.holdsSlot) {
            throw BusinessException(ReservationResult.RESERVATION_HOLD_NOT_EXTENDABLE)
        }

        reservation.extendHold(holdExpiresAt, now)
        log.info("[ReservationService] 선점 기간 연장: reservationId={}, holdExpiresAt={}", reservationId, holdExpiresAt)
    }

    /** 선점 기간이 지난 예약을 `EXPIRED` 로 종료한다. */
    @Transactional
    fun expireOverdueHolds(): List<SlotRef> {
        val now = now()
        val overdue = reservationRepository.findExpiredHoldsForUpdate(now, holdProperties.expiryBatchSize)
        overdue.forEach { expire(it, now) }
        if (overdue.isNotEmpty()) {
            log.info("[ReservationService] 선점 만료 처리: count={}", overdue.size)
        }
        return overdue.map { it.slot }
    }

    /** 예약 상세 조회 (어드민용) */
    @Transactional(readOnly = true)
    fun getReservationDetailForAdmin(reservationId: UUID): ReservationDetailResponse {
        val reservation = reservationRepository.findById(reservationId)
            ?: throw BusinessException(ReservationResult.RESERVATION_NOT_FOUND)

        return ReservationDetailResponse.of(reservation, consultationRepository.findAllByReservation(reservation))
    }

    private fun expire(reservation: ReservationEntity, now: OffsetDateTime) =
        transition(reservation, ReservationStatus.EXPIRED, note = "선점 기간 만료", now = now)

    /** 상태와 이력을 함께 바꾼다. */
    private fun transition(reservation: ReservationEntity, target: ReservationStatus, note: String?, now: OffsetDateTime) {
        reservation.changeStatus(target, holdExpiresAt = now.plus(holdProperties.duration), now = now)
        ReservationConsultationEntity.create(reservation, target, note)
    }
}
