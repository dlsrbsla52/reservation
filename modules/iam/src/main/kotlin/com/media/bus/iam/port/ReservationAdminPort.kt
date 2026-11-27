package com.media.bus.iam.port

import com.media.bus.iam.port.dto.reservation.*
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.*

/** 관리자 기능이 쓰는 reservation 모듈 포트 */
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

    fun updateContractStatus(contractId: UUID, requiredManagerId: UUID?, status: String, cancelReason: String?)

    fun updateContractPaymentStatus(
        contractId: UUID,
        requiredManagerId: UUID?,
        status: String,
        paidAmount: BigDecimal?,
        paidAt: OffsetDateTime?,
    )

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
