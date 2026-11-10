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

/**
 * ## 예약 도메인의 DB 저장/조회를 담당하는 서비스
 *
 * 설계 의도:
 * - `ReservationFacade` 가 Redis 게이트와 S2S 호출(Stop 검증)을 처리한 뒤 이 서비스를 호출한다.
 * - 현재 상태는 `reservation.status` 가 원본이고, `reservation_consultation` 은 append-only 변경 이력이다.
 *   상태를 바꿀 때는 항상 두 곳을 같은 트랜잭션에서 함께 쓴다.
 *
 * 선점 정책:
 * - 예약은 생성 즉시 정류소 광고면을 선점한다(선착순). 광고면당 진행 중 예약은 1건이다.
 * - 선점은 `hold.duration` 뒤에 만료된다. 상담이 시작되면 그 시점부터 다시 계산한다.
 * - 동시 요청의 승자는 DB 가 정한다 — 생성은 부분 유니크 인덱스, 상태 전이는 행 잠금.
 *   Redis 게이트는 부하를 줄일 뿐이므로 이 서비스는 게이트 없이 호출돼도 정합성을 지켜야 한다.
 *
 * 선점이 풀리는 메서드는 풀린 광고면([SlotRef])을 반환한다. 호출 측이 **커밋 후** Redis 점유 캐시를 지운다.
 */
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

    /**
     * 예약 + 초기 상담(PENDING) row 를 생성하고 광고면을 선점한다.
     *
     * @throws BusinessException 회원 선점 한도 초과, 희망 시작일에 계약 중, 이미 선점된 광고면인 경우
     */
    @Transactional
    fun createReservation(
        memberId: UUID,
        stopInfo: StopInfo,
        request: CreateStopReservationRequest,
        slotNo: Short = StopSlot.DEFAULT_NO,
    ): ReservationEntity {
        val now = now()

        // 회원당 선점 한도. 잠금 없이 세므로 같은 회원의 동시 요청은 몇 건 넘길 수 있다 — 독점 방지용 상한이라 허용한다.
        if (reservationRepository.countHoldingByMember(memberId) >= holdProperties.maxActivePerMember) {
            throw BusinessException(ReservationResult.RESERVATION_LIMIT_EXCEEDED)
        }

        // 희망 계약 시작일에 이미 계약이 점유한 광고면이면 상담해도 계약할 수 없으므로 예약 단계에서 거절한다.
        val desiredStart = (request.desiredContractStartDate ?: now.toLocalDate()).atStartOfDay(KST).toOffsetDateTime()
        if (contractService.isSlotOccupiedAt(stopInfo.id, slotNo, desiredStart)) {
            throw BusinessException(ReservationResult.STOP_UNDER_CONTRACT)
        }

        // 기존 선점 확인. 만료됐는데 스케줄러가 아직 정리하지 못한 선점은 여기서 정리하고 넘겨받는다.
        reservationRepository.findHolderForUpdate(stopInfo.id, slotNo)?.let { holder ->
            if (!holder.isHoldExpired(now)) {
                throw BusinessException(
                    if (holder.memberId == memberId) ReservationResult.RESERVATION_ALREADY_EXISTS
                    else ReservationResult.STOP_ALREADY_HELD,
                )
            }
            expire(holder, now)
            // 유니크 인덱스는 즉시 검사되므로, 새 예약 INSERT 보다 만료 UPDATE 가 먼저 나가야 한다.
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
        // 초기 상태 row 기록 (상태 이력 추적의 시작점)
        ReservationConsultationEntity.create(reservation, ReservationStatus.PENDING)

        // 선점 중인 예약이 없던 광고면에 동시 요청이 들어오면 잠글 행이 없어 함께 여기까지 온다.
        // 승자는 유니크 인덱스가 정하고, 진 요청은 여기서 409 로 바뀐다.
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

    /**
     * 본인 예약 단건 상세 조회. 소유자가 아니면 `RESERVATION_ACCESS_DENIED` 로 응답한다.
     * (존재 여부와 권한 여부를 구분하지 않고 `NOT_FOUND` 로 통합해도 되지만,
     *  현재는 명시적 403 응답으로 UI 에서 메시지 분기하기 쉽도록 유지.)
     */
    @Transactional(readOnly = true)
    fun getReservationDetail(memberId: UUID, reservationId: UUID): ReservationDetailResponse {
        val reservation = reservationRepository.findById(reservationId)
            ?: throw BusinessException(ReservationResult.RESERVATION_NOT_FOUND)

        if (reservation.memberId != memberId) {
            throw BusinessException(ReservationResult.RESERVATION_ACCESS_DENIED)
        }

        return ReservationDetailResponse.of(reservation, consultationRepository.findAllByReservation(reservation))
    }

    /**
     * 예약 취소 (soft). PENDING 상태에서만 허용하고 선점을 푼다.
     *
     * @return 선점이 풀린 광고면
     */
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

    /**
     * 어드민용 전체 예약 목록 검색 및 조건부 필터링 페이징 조회.
     * 결과 리스트와 전체 개수를 반환한다.
     */
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

    /**
     * 예약 상태 변경 및 상담 이력(메모) 기록.
     *
     * 예약 행을 잠근 뒤 전이 가능 여부를 검사하므로, 관리자 두 명이 동시에 바꾸거나
     * 회원 취소와 겹쳐도 먼저 커밋된 쪽만 반영되고 나중 요청은 전이 오류를 받는다.
     * `EXPIRED` 는 선점 기간 만료로 시스템만 기록하는 상태라 이 경로로는 받지 않는다.
     *
     * @return 이 변경으로 선점이 풀렸으면 그 광고면, 아니면 null
     */
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

    /**
     * 예약을 완료(COMPLETED) 처리하고 같은 트랜잭션에서 계약을 생성한다.
     *
     * 둘 중 하나만 반영되면 "계약 없는 완료 예약"이나 "예약이 살아 있는 계약"이 남으므로 반드시 함께 커밋한다.
     * 계약 기간이 기존 계약과 겹치면 계약 생성이 실패하고 예약 상태 변경도 함께 롤백된다.
     * 예약의 선점은 여기서 끝나고, 이후 광고면은 계약이 계약 기간 동안 점유한다.
     *
     * @param stopInfo stop 서비스에서 확인된 정류소 정보 (S2S 호출은 트랜잭션 밖에서 끝낸다)
     * @return 예약 선점이 풀린 광고면
     */
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
        // 선점 해제(UPDATE)가 계약 INSERT 보다 먼저 나가도록 순서를 고정한다.
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

    /**
     * 선점 만료 일시를 바꾼다(관리자 연장). 진행 중인 예약만 가능하다.
     *
     * @param holdExpiresAt 새 만료 일시. 현재 시각 이후여야 한다.
     */
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

    /**
     * 선점 기간이 지난 예약을 `EXPIRED` 로 종료한다. 만료 스케줄러가 주기적으로 호출한다.
     *
     * @return 선점이 풀린 광고면 목록. `hold.expiryBatchSize` 만큼 찼으면 남은 대상이 더 있을 수 있다.
     */
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

    /**
     * 상태와 이력을 함께 바꾼다. 호출 측은 예약 행을 잠그고 전이 가능 여부를 검증한 상태여야 한다.
     * 선점 상태로 가면(상담 시작) 그 시점부터 선점 기간을 다시 준다.
     */
    private fun transition(reservation: ReservationEntity, target: ReservationStatus, note: String?, now: OffsetDateTime) {
        reservation.changeStatus(target, holdExpiresAt = now.plus(holdProperties.duration), now = now)
        ReservationConsultationEntity.create(reservation, target, note)
    }
}
