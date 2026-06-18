package com.media.bus.iam.admin.controller

import com.media.bus.common.web.response.ApiResponse
import com.media.bus.common.web.wrapper.PageResult
import com.media.bus.contract.entity.member.MemberType
import com.media.bus.contract.entity.member.Permission
import com.media.bus.contract.security.annotation.Authorize
import com.media.bus.iam.admin.facade.AdminReservationFacade
import com.media.bus.iam.client.reservation.dto.*
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*
import java.util.*

/**
 * ## 어드민 사이트 전용 예약 관리 컨트롤러
 *
 * 어드민 매니저/마스터 권한을 가진 사용자가 예약을 제어한다.
 * 내부적으로 S2S API 클라이언트를 통해 reservation 모듈로 요청을 위임하여 실행한다.
 */
@Tag(
    name = "어드민 예약 관리",
    description = "어드민 사이트 전용 예약 관리 API. ADMIN_MASTER/ADMIN_DEVELOPER + MANAGE 권한 필요.",
)
@RestController
@Authorize(types = [MemberType.ADMIN_MASTER, MemberType.ADMIN_DEVELOPER], permissions = [Permission.MANAGE])
@RequestMapping("/api/v1/admin/reservation")
class AdminReservationController(
    private val adminReservationFacade: AdminReservationFacade,
) {
    companion object {
        private const val MAX_PAGE_SIZE = 100
    }

    /** 전체 예약 조건부 페이징 검색 */
    @Operation(
        summary = "전체 예약 조건 검색",
        description = "상태, 담당자 ID, 정류소 ID, 상담 신청 기간 등을 필터링하여 전체 예약을 페이징 검색합니다."
    )
    @GetMapping("/reservations")
    fun searchAdminReservations(
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) assigneeId: UUID?,
        @RequestParam(required = false) stopId: UUID?,
        @RequestParam(required = false) createdFrom: String?,
        @RequestParam(required = false) createdTo: String?,
        @Parameter(description = "0-base 페이지 번호") @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "20") size: Int,
    ): ApiResponse<PageResult<AdminReservationView>> {
        val safePage = page.coerceAtLeast(0)
        val safeSize = size.coerceIn(1, MAX_PAGE_SIZE)
        return ApiResponse.success(
            adminReservationFacade.searchAdminReservations(status, assigneeId, stopId, createdFrom, createdTo, safePage, safeSize)
        )
    }

    /** 예약 단건 상세 조회 */
    @Operation(
        summary = "예약 상세 조회",
        description = "특정 예약의 상세 정보와 상담 로그 전체를 함께 조회합니다."
    )
    @GetMapping("/reservations/{reservationId}")
    fun getReservationDetailForAdmin(
        @PathVariable reservationId: UUID
    ): ApiResponse<AdminReservationDetailResponse.AdminReservationDetail> =
        ApiResponse.success(adminReservationFacade.getReservationDetailForAdmin(reservationId))

    /** 예약 담당자 지정 */
    @Operation(
        summary = "예약 담당자 배정",
        description = "특정 예약에 담당 어드민을 배정 또는 변경합니다."
    )
    @PutMapping("/reservations/{reservationId}/assignee")
    fun assignReservation(
        @PathVariable reservationId: UUID,
        @RequestBody @Valid request: AssignReservationRequest
    ): ApiResponse<Unit?> {
        adminReservationFacade.assignReservation(reservationId, request.assigneeId)
        return ApiResponse.successWithMessage("예약 담당자가 배정되었습니다.")
    }

    /** 예약 상태 변경 */
    @Operation(
        summary = "예약 상태 변경",
        description = "예약의 상태를 변경하고 상담 로그(메모)를 누적 기록합니다."
    )
    @PutMapping("/reservations/{reservationId}/status")
    fun updateReservationStatus(
        @PathVariable reservationId: UUID,
        @RequestBody @Valid request: UpdateReservationStatusRequest
    ): ApiResponse<Unit?> {
        adminReservationFacade.updateReservationStatus(reservationId, request.status, request.note)
        return ApiResponse.successWithMessage("예약 상태 및 상담 기록이 반영되었습니다.")
    }

    /** 예약 완료 및 계약 생성 */
    @Operation(
        summary = "예약 완료 및 계약 생성",
        description = "예약을 상담 완료(COMPLETED) 처리하고 연계된 광고 계약(Contract)을 신규 생성합니다."
    )
    @PostMapping("/reservations/{reservationId}/complete-to-contract")
    fun completeReservationToContract(
        @PathVariable reservationId: UUID,
        @RequestBody @Valid request: CompleteToContractRequest
    ): ApiResponse<Unit?> {
        adminReservationFacade.completeReservationToContract(reservationId, request)
        return ApiResponse.successWithMessage("예약이 완료되었으며, 신규 계약서가 발행되었습니다.")
    }
}