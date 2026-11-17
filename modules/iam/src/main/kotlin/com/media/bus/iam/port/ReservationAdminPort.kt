package com.media.bus.iam.port

import com.media.bus.iam.port.dto.reservation.*
import java.time.OffsetDateTime
import java.util.*

/**
 * ## 관리자 기능이 쓰는 reservation 모듈 포트
 *
 * 설계 의도:
 * - iam은 reservation 모듈을 컴파일 의존하지 않는다. 소비자(iam)가 필요한 기능을 이 포트로 정의한다.
 * - 구현은 `app` 모듈의 `IamReservationAdminAdapter`이고, reservation의 Facade(`InternalContractFacade`, `ReservationFacade`)를
 *   직접 호출한다. DB Service로 건너뛰지 않는 것은 Facade가 하던 회원 재검증·정류소 결합·선점 캐시 해제를 지키기 위해서다.
 * - 메서드 계약(예외 상태·코드 포함)은 예전 HTTP 클라이언트(`ReservationServiceClient`)와 같게 유지한다.
 */
interface ReservationAdminPort {

    /** 전체 계약을 선택 조건으로 검색한다. */
    fun searchContracts(
        memberId: UUID?,
        stopId: UUID?,
        status: String?,
        keyword: String?,
        contractStartFrom: OffsetDateTime?,
        contractStartTo: OffsetDateTime?,
        page: Int,
        size: Int,
    ): AdminContractPage

    /** 관리자 요청으로 계약을 생성한다. */
    fun createContract(request: CreateContractRequest): ContractView

    /** 소유자 제한 없이 계약 상세를 조회한다. */
    fun getContractDetail(contractId: UUID): AdminContractView

    /** 매니저의 계약 목록을 페이지 단위로 조회한다. */
    fun getContractsByMember(memberId: UUID, page: Int, size: Int): AdminContractPage

    /** 전체 예약을 조건부로 검색한다. */
    fun searchAdminReservations(
        status: String?,
        assigneeId: UUID?,
        stopId: UUID?,
        createdFrom: String?,
        createdTo: String?,
        page: Int,
        size: Int,
    ): AdminReservationPage

    /** 예약 상세를 조회한다. */
    fun getReservationDetailForAdmin(reservationId: UUID): AdminReservationDetail

    /** 예약 담당자를 지정한다. */
    fun assignReservation(reservationId: UUID, assigneeId: UUID)

    /** 예약 상태를 변경하고 상담 기록을 추가한다. */
    fun updateReservationStatus(reservationId: UUID, status: String, note: String?)

    /** 진행 중인 예약의 선점 만료 일시를 변경한다. */
    fun extendHold(reservationId: UUID, holdExpiresAt: OffsetDateTime)

    /** 예약을 완료 처리하고 광고 계약으로 전환한다. */
    fun completeReservationToContract(reservationId: UUID, request: CompleteToContractRequest)
}
