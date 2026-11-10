package com.media.bus.reservation.reservation.service

import com.media.bus.common.exceptions.BaseException
import com.media.bus.common.result.Result
import com.media.bus.common.result.type.CommonResult
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

/**
 * ## 예약 흐름을 조율하는 Facade
 *
 * MSA 환경에서 SQS/이벤트 기반 분산 트랜잭션 적용이 어려운 현재 단계에서,
 * 각 서비스 호출을 순서대로 조합하고 트랜잭션을 명시적으로 분리한다.
 *
 * 예약 생성 흐름(정류소마다 독립 수행):
 * 1단계: `StopHoldGate` → Redis 게이트. 같은 광고면에 몰린 요청 중 1건만 통과 (트랜잭션 외부)
 * 2단계: `StopResolutionService` → 정류소 유효성 확인 (S2S, 트랜잭션 외부)
 * 3단계: `ReservationService` → 선점 판정 + 예약/상담 DB 저장 (`@Transactional`)
 * 4단계: 커밋 결과에 따라 게이트 키를 점유 캐시로 바꾸거나 해제
 *
 * Redis 연산은 모두 트랜잭션 밖에 둔다. 게이트 키는 커밋된 사실만 반영해야 하고,
 * 트랜잭션 안에서 Redis 를 기다리면 DB 커넥션을 쥔 채 대기하게 된다.
 */
@Service
class ReservationFacade(
    private val stopResolutionService: StopResolutionService,
    private val reservationService: ReservationService,
    private val stopHoldGate: StopHoldGate,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 요청한 정류소마다 선착순 선점을 시도하고 정류소별 결과를 돌려준다.
     *
     * 정류소 하나의 실패가 다른 정류소의 예약을 막지 않는다(3개 중 1개가 이미 선점돼도 2개는 예약된다).
     * 그래서 실패를 예외로 던지지 않고 결과 항목에 담는다.
     *
     * @param principal JWT 클레임 기반 인증 정보 (회원 ID 원천)
     * @param request   예약 생성 요청 DTO
     * @return 정류소별 예약 결과
     */
    fun createReservation(principal: MemberPrincipal, request: CreateStopReservationRequest): CreateReservationResponse =
        CreateReservationResponse.of(request.stopId.map { reserveOne(principal.id, it, request) })

    private fun reserveOne(
        memberId: UUID,
        stopId: UUID,
        request: CreateStopReservationRequest,
    ): CreateReservationResponse.Item {
        val slotNo = StopSlot.DEFAULT_NO

        // 1단계: 게이트. 못 잡으면 다른 요청이 처리 중이거나 이미 선점된 광고면이다 — S2S·DB 를 쓰지 않고 끝낸다.
        val pass = stopHoldGate.tryEnter(stopId, slotNo)
            ?: return failure(stopId, ReservationResult.STOP_ALREADY_HELD)

        return try {
            // 2단계: 정류소 존재 확인 (stop S2S 호출은 트랜잭션 외부)
            val stop = stopResolutionService.resolveStop(stopId)
            // 3단계: 선점 판정 + 예약/초기 상담 row 저장 (단일 트랜잭션)
            val reservation = reservationService.createReservation(memberId, stop, request, slotNo)
            // 4단계: 커밋됐으므로 게이트 키를 점유 캐시로 전환
            stopHoldGate.confirm(pass)
            CreateReservationResponse.Item(
                stopId = stopId,
                success = true,
                reservationId = reservation.id.value,
                holdExpiresAt = reservation.holdExpiresAt,
            )
        } catch (e: BaseException) {
            if (e.result in OCCUPIED_RESULTS) {
                // DB 가 "이미 선점됨"이라고 답했다. 같은 광고면으로 오는 재요청이 DB 까지 가지 않게 점유 캐시를 다시 건다.
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

    /**
     * 내 예약 목록을 조회하고 각 항목에 정류소 정보를 결합하여 반환한다.
     *
     * 흐름:
     * 1단계: DB에서 예약 목록 조회 (단일 트랜잭션, stop 정보는 null 상태)
     * 2단계: 중복 제거된 stopId 집합에 대해 **1회 S2S 일괄 조회** → N+1 방지
     * 3단계: 응답 row별로 stopName/stopNumber를 주입
     *
     * Stop 서비스 장애 시 `StopResolutionService.resolveStops`가 빈 맵으로 fallback 하므로
     * 예약 목록 자체는 정상 반환된다 (stopName/stopNumber는 null).
     */
    fun getMyReservations(principal: MemberPrincipal, page: Int, size: Int): PageResult<MyReservationResponse> =
        enrichWithStops(reservationService.getMyReservations(principal.id, page, size))

    /**
     * 특정 회원의 예약 목록을 조회한다(어드민 전용).
     *
     * `getMyReservations`와 동일한 정류소 결합 로직을 사용하되, 본인(principal)이 아닌
     * 임의의 `memberId`를 대상으로 한다. 인가는 컨트롤러(`@Authorize`)에서 ADMIN 권한으로 강제하며,
     * 이 메서드 자체는 소유권 검증을 하지 않는다(어드민은 타 회원 예약 조회가 정당한 동작).
     *
     * **모듈 경계**: 회원 존재 여부/유형 판별은 iam 책임이므로 여기서 확인하지 않는다.
     * 존재하지 않는 회원이면 빈 페이지가 반환된다.
     *
     * @param memberId 조회 대상 회원 ID
     */
    fun getMemberReservations(memberId: UUID, page: Int, size: Int): PageResult<MyReservationResponse> =
        enrichWithStops(reservationService.getMyReservations(memberId, page, size))

    /**
     * 예약 목록 페이지의 각 row에 정류소 정보(stopNumber/stopName)를 결합한다.
     *
     * 중복 제거된 stopId 집합에 대해 **1회 S2S 일괄 조회**로 N+1을 방지하며,
     * stop 서비스 장애 시 `resolveStops`가 빈 맵으로 fallback 하여 목록 자체는 정상 반환된다.
     */
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

    /**
     * 예약 단건 상세에 정류소 정보를 결합하여 반환한다.
     *
     * 단건 상세는 stopId가 1개이므로 bulk 대신 단건 조회를 사용해도 무방하나,
     * 실패 시 graceful fallback을 위해 `resolveStops(setOf(id))`로 통일한다.
     */
    fun getReservationDetail(principal: MemberPrincipal, reservationId: UUID): ReservationDetailResponse {
        val raw = reservationService.getReservationDetail(principal.id, reservationId)
        val info = stopResolutionService.resolveStops(setOf(raw.stopId))[raw.stopId]
        return raw.copy(stopNumber = info?.stopId, stopName = info?.stopName)
    }

    /**
     * 어드민용 전체 예약 목록 검색 및 정류소 정보 일괄 결합 페이징 조회.
     * N+1 방지를 위해 정류소 정보는 1회 S2S 벌크 호출로 주입한다.
     */
    fun searchAdminReservations(
        status: ReservationStatus?,
        assigneeId: UUID?,
        stopId: UUID?,
        createdFrom: OffsetDateTime?,
        createdTo: OffsetDateTime?,
        page: Int,
        size: Int
    ): PageResult<AdminReservationListResponse> {
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

    /** 예약 상태 변경 및 상담 기록 추가. 선점이 풀리는 전이면 커밋 후 점유 캐시를 지운다. */
    fun updateReservationStatus(reservationId: UUID, targetStatus: ReservationStatus, note: String?) {
        reservationService.updateReservationStatus(reservationId, targetStatus, note)?.let(stopHoldGate::evict)
    }

    /** 선점 기간 연장 (어드민용) */
    fun extendHold(reservationId: UUID, holdExpiresAt: OffsetDateTime) {
        reservationService.extendHold(reservationId, holdExpiresAt)
    }

    /**
     * 선점 기간이 지난 예약을 만료시키고 풀린 광고면의 점유 캐시를 지운다. 만료 스케줄러가 호출한다.
     *
     * @return 이번 호출에서 만료 처리한 예약 수
     */
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

    /**
     * 예약을 완료 처리하고 해당 예약건을 기반으로 즉시 광고 계약을 생성한다.
     *
     * S2S 정류소 유효성 검증은 트랜잭션 외부에서 수행하고, 예약 완료와 계약 생성은
     * `ReservationService.completeToContract` 의 단일 트랜잭션으로 함께 커밋한다.
     */
    fun completeReservationToContract(reservationId: UUID, request: CompleteToContractRequest) {
        val detail = reservationService.getReservationDetailForAdmin(reservationId)
        val stopInfo = stopResolutionService.resolveStop(detail.stopId)

        val released = reservationService.completeToContract(reservationId, stopInfo, request)
        stopHoldGate.evict(released)
    }

    private companion object {
        /** DB 가 "이 광고면은 이미 선점됐다"고 판정한 결과 코드. */
        val OCCUPIED_RESULTS: Set<Result> =
            setOf(ReservationResult.STOP_ALREADY_HELD, ReservationResult.RESERVATION_ALREADY_EXISTS)
    }
}
