package com.media.bus.reservation.reservation.service

import com.media.bus.common.web.wrapper.PageResult
import com.media.bus.contract.security.MemberPrincipal
import com.media.bus.reservation.contract.dto.request.CreateContractRequest
import com.media.bus.reservation.contract.service.ContractService
import com.media.bus.reservation.reservation.dto.request.CompleteToContractRequest
import com.media.bus.reservation.reservation.dto.request.CreateStopReservationRequest
import com.media.bus.reservation.reservation.dto.response.AdminReservationListResponse
import com.media.bus.reservation.reservation.dto.response.MyReservationResponse
import com.media.bus.reservation.reservation.dto.response.ReservationDetailResponse
import com.media.bus.reservation.reservation.entity.enums.ReservationStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
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
    private val contractService: ContractService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 예약을 생성한다. 저장 결과의 예약 ID 를 반환하여 컨트롤러/클라이언트가 후속 조회에 사용할 수 있게 한다.
     *
     * @param principal JWT 클레임 기반 인증 정보 (회원 ID 원천)
     * @param request   예약 생성 요청 DTO
     * @return 생성된 예약의 UUID
     */
    fun createReservation(principal: MemberPrincipal, request: CreateStopReservationRequest): Set<UUID> {
        // 1단계: 요청된 정류소 각각에 대해 존재 여부 확인 후 예약 생성 (stop S2S 호출은 트랜잭션 외부)
        return request.stopId.map { stopId ->
            val stop = stopResolutionService.resolveStop(stopId)
            log.debug("[ReservationFacade] 정류소 확인 완료: stopName={}, memberId={}", stop.stopName, principal.id)
            // 2단계: 예약 + 초기 상담 row 저장 (단일 트랜잭션)
            reservationService.createReservation(principal.id, stop, request).id.value
        }.toSet()
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
        val (rawList, total) = reservationService.searchAdminReservations(status, assigneeId, stopId, createdFrom, createdTo, page, size)

        val stopIds = rawList.map { it.first.stopId }.toSet()
        val stopMap = stopResolutionService.resolveStops(stopIds)

        val items = rawList.map { (reservation, currentStatus) ->
            val info = stopMap[reservation.stopId]
            AdminReservationListResponse.of(reservation, currentStatus, info?.stopId, info?.stopName)
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

    /** 예약 상태 변경 및 상담 기록 추가 */
    fun updateReservationStatus(reservationId: UUID, targetStatus: ReservationStatus, note: String?) {
        reservationService.updateReservationStatus(reservationId, targetStatus, note)
    }

    /** 예약 상세 및 상담 전체 이력 조회 (어드민용) */
    fun getReservationDetailForAdmin(reservationId: UUID): ReservationDetailResponse {
        val raw = reservationService.getReservationDetailForAdmin(reservationId)
        val info = stopResolutionService.resolveStops(setOf(raw.stopId))[raw.stopId]
        return raw.copy(stopNumber = info?.stopId, stopName = info?.stopName)
    }

    /**
     * 예약을 완료 처리하고 해당 예약건을 기반으로 즉시 광고 계약을 생성한다.
     * S2S 정류소 유효성 검증은 트랜잭션 외부에서 수행하고, 각 처리를 순차 조율한다.
     */
    fun completeReservationToContract(reservationId: UUID, request: CompleteToContractRequest) {
        val detail = reservationService.getReservationDetailForAdmin(reservationId)
        val stopInfo = stopResolutionService.resolveStop(detail.stopId)

        // 1단계: 예약 상태 변경 (COMPLETED)
        reservationService.updateReservationStatus(reservationId, ReservationStatus.COMPLETED, request.note)

        // 2단계: 계약서 생성 및 연결
        val createContractRequest = CreateContractRequest(
            stopId = detail.stopId,
            contractName = request.contractName,
            totalAmount = request.totalAmount,
            payAmount = request.payAmount,
            paymentCycle = request.paymentCycle,
            paymentMethod = request.paymentMethod,
            memberId = detail.memberId,
            note = request.note,
            contractStartDate = request.contractStartDate,
            contractEndDate = request.contractEndDate
        )
        contractService.createContract(stopInfo, createContractRequest)
    }
}
