package com.media.bus.reservation.reservation.service

import com.media.bus.common.web.wrapper.PageResult
import com.media.bus.contract.security.MemberPrincipal
import com.media.bus.reservation.reservation.dto.request.CreateStopReservationRequest
import com.media.bus.reservation.reservation.dto.response.MyReservationResponse
import com.media.bus.reservation.reservation.dto.response.ReservationDetailResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.*

/**
 * ## 예약 생성 흐름을 조율하는 Facade
 *
 * MSA 환경에서 SQS/이벤트 기반 분산 트랜잭션 적용이 어려운 현재 단계에서,
 * 각 서비스 호출을 순서대로 조합하고 트랜잭션을 명시적으로 분리한다.
 *
 * 흐름:
 * 1단계: `StopResolutionService` → 정류소 유효성 확인 (S2S, 트랜잭션 외부)
 * 2단계: `ReservationService` → 중복 검사 + 예약/상담 DB 저장 (`@Transactional`)
 */
@Service
class ReservationFacade(
    private val stopResolutionService: StopResolutionService,
    private val reservationService: ReservationService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 예약을 생성한다. 저장 결과의 예약 ID 를 반환하여 컨트롤러/클라이언트가 후속 조회에 사용할 수 있게 한다.
     *
     * @param principal JWT 클레임 기반 인증 정보 (회원 ID 원천)
     * @param request   예약 생성 요청 DTO
     * @return 생성된 예약의 UUID
     */
    fun createReservation(principal: MemberPrincipal, request: CreateStopReservationRequest): UUID {
        // 1단계: stop 서비스에서 정류소 존재 여부 확인 (S2S, 트랜잭션 외부)
        val stop = stopResolutionService.resolveStop(request.stopId)
        log.debug("[ReservationFacade] 정류소 확인 완료: stopName={}, memberId={}", stop.stopName, principal.id)

        // 2단계: 예약 + 초기 상담 row 저장 (단일 트랜잭션)
        val reservation = reservationService.createReservation(principal.id, stop, request)
        return reservation.id.value
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
}
