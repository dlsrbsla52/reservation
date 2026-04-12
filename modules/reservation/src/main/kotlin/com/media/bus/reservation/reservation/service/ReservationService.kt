package com.media.bus.reservation.reservation.service

import com.media.bus.common.exceptions.BusinessException
import com.media.bus.common.web.wrapper.PageResult
import com.media.bus.reservation.reservation.dto.request.CreateStopReservationRequest
import com.media.bus.reservation.reservation.dto.response.MyReservationResponse
import com.media.bus.reservation.reservation.dto.response.ReservationDetailResponse
import com.media.bus.reservation.reservation.dto.response.StopInfo
import com.media.bus.reservation.reservation.entity.ReservationConsultationEntity
import com.media.bus.reservation.reservation.entity.ReservationEntity
import com.media.bus.reservation.reservation.entity.enums.ReservationStatus
import com.media.bus.reservation.reservation.repository.ReservationConsultationRepository
import com.media.bus.reservation.reservation.repository.ReservationRepository
import com.media.bus.reservation.reservation.result.ReservationResult
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

/**
 * ## 예약 도메인의 DB 저장/조회를 담당하는 서비스
 *
 * 설계 의도:
 * - `ReservationFacade` 가 S2S 호출(Stop 검증) 후 이 서비스를 호출한다.
 * - 상태(PENDING/CONSULTING/COMPLETED/CANCELLED)는 `reservation_consultation` 테이블에 append-only 로
 *   기록하며, 최신 row 가 현재 상태를 나타낸다. → 감사 추적 및 상담 이력 보존.
 * - 상태 변경(취소 등)은 기존 row 수정이 아닌 새 row 추가로 표현한다.
 */
@Service
class ReservationService(
    private val reservationRepository: ReservationRepository,
    private val consultationRepository: ReservationConsultationRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 예약 + 초기 상담(PENDING) row 를 생성한다.
     *
     * @throws BusinessException 동일 회원·정류소에 이미 진행 중인 예약이 있는 경우
     */
    @Transactional
    fun createReservation(
        memberId: UUID,
        stopInfo: StopInfo,
        request: CreateStopReservationRequest,
    ): ReservationEntity {
        // 중복 예약 방지: 동일 정류소에 PENDING/CONSULTING 상태의 예약이 있으면 거부
        if (reservationRepository.existsActiveByMemberAndStop(memberId, stopInfo.id)) {
            throw BusinessException(ReservationResult.RESERVATION_ALREADY_EXISTS)
        }

        val reservation = ReservationEntity.create(
            memberId = memberId,
            stopId = stopInfo.id,
            consultationRequestedAt = request.consultationRequestedAt,
            desiredContractStartDate = request.desiredContractStartDate,
        )
        // 초기 상태 row 기록 (상태 이력 추적의 시작점)
        ReservationConsultationEntity.create(reservation, ReservationStatus.PENDING)

        log.debug(
            "[ReservationService] 예약 저장 완료: reservationId={}, memberId={}, stopId={}",
            reservation.id.value, memberId, stopInfo.id,
        )
        return reservation
    }

    /** 본인 예약 목록 조회 (페이지네이션). */
    @Transactional(readOnly = true)
    fun getMyReservations(memberId: UUID, page: Int, size: Int): PageResult<MyReservationResponse> {
        val reservations = reservationRepository.findByMemberIdPaged(memberId, page, size)
        val totalCnt = reservationRepository.countByMemberId(memberId)

        val items = reservations.map { reservation ->
            val status = resolveCurrentStatus(reservation)
            MyReservationResponse.of(reservation, status)
        }
        return PageResult(
            items = items,
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

        val consultations = consultationRepository.findAllByReservation(reservation)
        val currentStatus = consultations.maxByOrNull { it.createdAt }?.status
            ?: throw BusinessException(ReservationResult.RESERVATION_STATE_MISSING)

        return ReservationDetailResponse.of(reservation, currentStatus, consultations)
    }

    /**
     * 예약 취소 (soft).
     *
     * PENDING 상태에서만 허용하고, 새 상담 row 를 `CANCELLED` 상태로 추가해 이력을 보존한다.
     * 상태 전이 검증은 `ReservationStatus.canTransitionTo()` 를 사용한다.
     */
    @Transactional
    fun cancelReservation(memberId: UUID, reservationId: UUID) {
        val reservation = reservationRepository.findByIdAndMemberId(reservationId, memberId)
            ?: throw BusinessException(ReservationResult.RESERVATION_NOT_FOUND)

        val latest = consultationRepository.findLatestByReservation(reservation)
            ?: throw BusinessException(ReservationResult.RESERVATION_STATE_MISSING)

        if (!latest.status.canTransitionTo(ReservationStatus.CANCELLED)) {
            throw BusinessException(ReservationResult.RESERVATION_NOT_CANCELLABLE)
        }

        ReservationConsultationEntity.create(reservation, ReservationStatus.CANCELLED)
        log.debug(
            "[ReservationService] 예약 취소 처리: reservationId={}, memberId={}",
            reservation.id.value, memberId,
        )
    }

    /** 예약 엔티티의 현재 상태를 최신 상담 row 에서 해석한다. */
    private fun resolveCurrentStatus(reservation: ReservationEntity): ReservationStatus =
        consultationRepository.findLatestByReservation(reservation)?.status
            ?: ReservationStatus.PENDING
}
