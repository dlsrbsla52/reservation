package com.media.bus.app.adapter

import com.media.bus.common.exceptions.BusinessException
import com.media.bus.common.result.type.CommonResult
import com.media.bus.common.web.wrapper.PageResult
import com.media.bus.iam.port.ReservationAdminPort
import com.media.bus.iam.port.dto.reservation.*
import com.media.bus.reservation.contract.dto.request.AdminContractSearchCondition
import com.media.bus.reservation.contract.entity.enums.ContractStatus
import com.media.bus.reservation.contract.entity.enums.PaymentCycle
import com.media.bus.reservation.contract.entity.enums.PaymentMethod
import com.media.bus.reservation.contract.service.InternalContractFacade
import com.media.bus.reservation.reservation.dto.response.AdminReservationListResponse
import com.media.bus.reservation.reservation.dto.response.ReservationDetailResponse
import com.media.bus.reservation.reservation.entity.enums.ReservationStatus
import com.media.bus.reservation.reservation.service.ReservationFacade
import org.springframework.stereotype.Component
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import java.util.*
import com.media.bus.iam.port.dto.reservation.AdminContractView as IamAdminContractView
import com.media.bus.iam.port.dto.reservation.CompleteToContractRequest as IamCompleteToContractRequest
import com.media.bus.iam.port.dto.reservation.CreateContractRequest as IamCreateContractRequest
import com.media.bus.reservation.contract.dto.request.CreateContractRequest as ReservationCreateContractRequest
import com.media.bus.reservation.contract.dto.response.AdminContractView as ReservationAdminContractView
import com.media.bus.reservation.reservation.dto.request.CompleteToContractRequest as ReservationCompleteToContractRequest

/**
 * ## iam 관리자 기능 → reservation 어댑터
 *
 * 예전 `InternalContractController`·`InternalReservationController`가 위임하던 Facade를 직접 부른다.
 * DB Service로 건너뛰지 않는다 — Facade가 회원 재검증, 정류소 결합, 선점 캐시 해제(`stopHoldGate.evict`)를 맡는다.
 *
 * - 입력 검증(DTO 제약, 검색 조건, 페이지 보정)은 Facade 진입점에 있다.
 * - 어댑터가 하는 일: iam의 문자열 코드값 → reservation enum 변환(실패 시 400), DTO 필드 변환.
 * - 트랜잭션은 두지 않는다. Facade는 트랜잭션 밖에서 정류소를 확인하고, DB 작업은 각 Service의 `@Transactional`이 연다.
 */
@Component
class IamReservationAdminAdapter(
    private val internalContractFacade: InternalContractFacade,
    private val reservationFacade: ReservationFacade,
) : ReservationAdminPort {

    override fun searchContracts(
        memberId: UUID?,
        stopId: UUID?,
        status: String?,
        keyword: String?,
        contractStartFrom: OffsetDateTime?,
        contractStartTo: OffsetDateTime?,
        page: Int,
        size: Int,
    ): AdminContractPage = callProvider {
        val condition = AdminContractSearchCondition(
            memberId = memberId,
            stopId = stopId,
            status = parseOptionalCode(status, "status", ContractStatus::fromName),
            keyword = keyword,
            contractStartFrom = contractStartFrom,
            contractStartTo = contractStartTo,
        )
        toContractPage(internalContractFacade.searchContracts(condition, page, size))
    }

    override fun createContract(request: IamCreateContractRequest): ContractView = callProvider {
        val contract = internalContractFacade.createContract(
            ReservationCreateContractRequest(
                stopId = request.stopId,
                contractName = request.contractName,
                totalAmount = request.totalAmount,
                payAmount = request.payAmount,
                paymentCycle = parseCode(request.paymentCycle, "paymentCycle", PaymentCycle::fromName),
                paymentMethod = parseCode(request.paymentMethod, "paymentMethod", PaymentMethod::fromName),
                memberId = request.memberId,
                note = request.note,
                contractStartDate = request.contractStartDate,
                contractEndDate = request.contractEndDate,
            ),
        )
        ContractView(
            id = contract.id,
            stopId = contract.stopId,
            stopNumber = contract.stopNumber,
            stopName = contract.stopName,
            memberId = contract.memberId,
            contractName = contract.contractName,
            status = contract.status.name,
            contractStartDate = contract.contractStartDate,
            contractEndDate = contract.contractEndDate,
            createdAt = contract.createdAt,
        )
    }

    override fun getContractDetail(contractId: UUID): IamAdminContractView =
        callProvider { toAdminContractView(internalContractFacade.getContractDetail(contractId)) }

    override fun getContractsByMember(memberId: UUID, page: Int, size: Int): AdminContractPage =
        callProvider { toContractPage(internalContractFacade.getContractsByMemberId(memberId, page, size)) }

    override fun searchAdminReservations(
        status: String?,
        assigneeId: UUID?,
        stopId: UUID?,
        createdFrom: String?,
        createdTo: String?,
        page: Int,
        size: Int,
    ): AdminReservationPage = callProvider {
        val result = reservationFacade.searchAdminReservations(
            parseOptionalCode(status, "status", ReservationStatus::fromName),
            assigneeId,
            stopId,
            parseDateTime(createdFrom, "createdFrom"),
            parseDateTime(createdTo, "createdTo"),
            page,
            size,
        )
        AdminReservationPage(
            items = result.items.map(::toAdminReservationView),
            totalCnt = result.totalCnt,
            pageRows = result.pageRows,
            pageNum = result.pageNum,
        )
    }

    override fun getReservationDetailForAdmin(reservationId: UUID): AdminReservationDetail =
        callProvider { toAdminReservationDetail(reservationFacade.getReservationDetailForAdmin(reservationId)) }

    override fun assignReservation(reservationId: UUID, assigneeId: UUID) =
        callProvider { reservationFacade.assignReservation(reservationId, assigneeId) }

    override fun updateReservationStatus(reservationId: UUID, status: String, note: String?) = callProvider {
        reservationFacade.updateReservationStatus(
            reservationId,
            parseCode(status, "status", ReservationStatus::fromName),
            note,
        )
    }

    override fun extendHold(reservationId: UUID, holdExpiresAt: OffsetDateTime) =
        callProvider { reservationFacade.extendHold(reservationId, holdExpiresAt) }

    override fun completeReservationToContract(reservationId: UUID, request: IamCompleteToContractRequest) = callProvider {
        reservationFacade.completeReservationToContract(
            reservationId,
            ReservationCompleteToContractRequest(
                contractName = request.contractName,
                totalAmount = request.totalAmount,
                payAmount = request.payAmount,
                paymentCycle = parseCode(request.paymentCycle, "paymentCycle", PaymentCycle::fromName),
                paymentMethod = parseCode(request.paymentMethod, "paymentMethod", PaymentMethod::fromName),
                contractStartDate = request.contractStartDate,
                contractEndDate = request.contractEndDate,
                note = request.note,
            ),
        )
    }

    /** ISO-8601 일시 문자열. 예전 `@DateTimeFormat(iso = DATE_TIME)` 파라미터 변환처럼 형식 오류는 400. */
    private fun parseDateTime(raw: String?, field: String): OffsetDateTime? {
        val value = raw?.takeIf { it.isNotBlank() } ?: return null
        return try {
            OffsetDateTime.parse(value)
        } catch (e: DateTimeParseException) {
            throw BusinessException(CommonResult.VALIDATION_FAIL, "허용되지 않는 값입니다: $field=$value", e)
        }
    }

    private fun toContractPage(page: PageResult<ReservationAdminContractView>) =
        AdminContractPage(
            items = page.items.map(::toAdminContractView),
            totalCnt = page.totalCnt,
            pageRows = page.pageRows,
            pageNum = page.pageNum,
        )

    private fun toAdminContractView(view: ReservationAdminContractView) = IamAdminContractView(
        id = view.id,
        stopId = view.stopId,
        stopNumber = view.stopNumber,
        stopName = view.stopName,
        memberId = view.memberId,
        contractName = view.contractName,
        status = view.status.name,
        contractStartDate = view.contractStartDate,
        contractEndDate = view.contractEndDate,
        totalAmount = view.totalAmount,
        payAmount = view.payAmount,
        paymentCycle = view.paymentCycle?.name,
        paymentMethod = view.paymentMethod?.name,
        paymentStatus = view.paymentStatus?.name,
        paidAmount = view.paidAmount,
        paidAt = view.paidAt,
        createdAt = view.createdAt,
    )

    private fun toAdminReservationView(row: AdminReservationListResponse) = AdminReservationView(
        reservationId = row.reservationId,
        memberId = row.memberId,
        stopId = row.stopId,
        stopNumber = row.stopNumber,
        stopName = row.stopName,
        status = row.status.name,
        holdExpiresAt = row.holdExpiresAt,
        assigneeId = row.assigneeId,
        consultationRequestedAt = row.consultationRequestedAt,
        desiredContractStartDate = row.desiredContractStartDate,
        createdAt = row.createdAt,
    )

    private fun toAdminReservationDetail(detail: ReservationDetailResponse) =
        AdminReservationDetail(
            reservationId = detail.reservationId,
            memberId = detail.memberId,
            stopId = detail.stopId,
            stopNumber = detail.stopNumber,
            stopName = detail.stopName,
            status = detail.status.name,
            holdExpiresAt = detail.holdExpiresAt,
            consultationRequestedAt = detail.consultationRequestedAt,
            desiredContractStartDate = detail.desiredContractStartDate,
            createdAt = detail.createdAt,
            assigneeId = detail.assigneeId,
            consultations = detail.consultations.map {
                AdminReservationDetail.ConsultationHistory(
                    consultationId = it.consultationId,
                    status = it.status.name,
                    note = it.note,
                    createdAt = it.createdAt,
                )
            },
        )
}
