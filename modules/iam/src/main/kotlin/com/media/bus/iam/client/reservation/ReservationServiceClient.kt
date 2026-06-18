package com.media.bus.iam.client.reservation

import com.media.bus.iam.client.reservation.dto.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.*

/**
 * ## reservation 서비스 내부 API 클라이언트
 *
 * 설계 의도:
 * - `ReservationApi`(`@HttpExchange` 프록시)에 HTTP 호출을 위임하고,
 *   응답 변환(null 체크, 페이지 메타 fallback) 등 비즈니스 로직을 이 클래스에서 처리한다.
 * - 소비자(`AdminBusinessContractFacade`)의 공개 API는 변경하지 않는다.
 */
@Component
class ReservationServiceClient(
    private val reservationApi: ReservationApi,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 매니저(memberId)의 계약 목록을 페이지 단위로 조회한다.
     *
     * reservation 응답이 null이거나 `data` 필드가 비어있으면 빈 페이지로 fallback 한다.
     * 호출부는 별도의 null 체크 없이 응답 구조만 그대로 신뢰할 수 bedrock.
     *
     * @param memberId 조회 대상 매니저 회원 ID
     * @param page     0-base 페이지 번호 (요청한 값 그대로 응답 메타에 fallback)
     * @param size     페이지 크기 (요청한 값 그대로 응답 메타에 fallback)
     * @return 정류소 정보가 결합된 어드민 계약 페이지 데이터
     */
    fun getContractsByMember(
        memberId: UUID,
        page: Int,
        size: Int,
    ): AdminContractPageResponse.AdminContractPage {
        log.debug(
            "[ReservationServiceClient] 매니저 계약 조회 요청: memberId={}, page={}, size={}",
            memberId, page, size,
        )
        val data = reservationApi.getContractsByMember(memberId, page, size)?.data
        return AdminContractPageResponse.AdminContractPage(
            items = data?.items ?: emptyList(),
            totalCnt = data?.totalCnt ?: 0L,
            pageRows = data?.pageRows ?: size,
            pageNum = data?.pageNum ?: page,
        )
    }

    /** 전체 예약 검색 및 조건부 필터링 페이징 조회 */
    fun searchAdminReservations(
        status: String?,
        assigneeId: UUID?,
        stopId: UUID?,
        createdFrom: String?,
        createdTo: String?,
        page: Int,
        size: Int,
    ): AdminReservationPageResponse.AdminReservationPage {
        log.debug(
            "[ReservationServiceClient] 예약 조건 검색 요청: status={}, assigneeId={}, stopId={}, page={}, size={}",
            status, assigneeId, stopId, page, size
        )
        val data = reservationApi.searchAdminReservations(status, assigneeId, stopId, createdFrom, createdTo, page, size)?.data
        return AdminReservationPageResponse.AdminReservationPage(
            items = data?.items ?: emptyList(),
            totalCnt = data?.totalCnt ?: 0L,
            pageRows = data?.pageRows ?: size,
            pageNum = data?.pageNum ?: page
        )
    }

    /** 예약 상세 조회 */
    fun getReservationDetailForAdmin(reservationId: UUID): AdminReservationDetailResponse.AdminReservationDetail {
        log.debug("[ReservationServiceClient] 예약 상세 조회 요청: reservationId={}", reservationId)
        return reservationApi.getReservationDetailForAdmin(reservationId)?.data
            ?: throw IllegalStateException("예약 상세 정보를 가져올 수 없습니다.")
    }

    /** 예약 담당자 지정 */
    fun assignReservation(reservationId: UUID, assigneeId: UUID) {
        log.debug("[ReservationServiceClient] 예약 담당자 지정 요청: reservationId={}, assigneeId={}", reservationId, assigneeId)
        val response = reservationApi.assignReservation(reservationId, AssignReservationRequest(assigneeId))
        if (response == null || response.code != "00000") {
            throw IllegalStateException("예약 담당자 배정에 실패하였습니다. ${response?.message ?: ""}")
        }
    }

    /** 예약 상태 변경 및 상담 기록 추가 */
    fun updateReservationStatus(reservationId: UUID, status: String, note: String?) {
        log.debug("[ReservationServiceClient] 예약 상태 변경 요청: reservationId={}, status={}, note={}", reservationId, status, note)
        val response = reservationApi.updateReservationStatus(reservationId, UpdateReservationStatusRequest(status, note))
        if (response == null || response.code != "00000") {
            throw IllegalStateException("예약 상태 변경에 실패하였습니다. ${response?.message ?: ""}")
        }
    }

    /** 예약을 완료 처리하고 광고 계약으로 전환 생성 */
    fun completeReservationToContract(reservationId: UUID, request: CompleteToContractRequest) {
        log.debug("[ReservationServiceClient] 예약 완료 및 계약 전환 요청: reservationId={}", reservationId)
        val response = reservationApi.completeReservationToContract(reservationId, request)
        if (response == null || response.code != "00000") {
            throw IllegalStateException("예약 완료 및 계약 전환에 실패하였습니다. ${response?.message ?: ""}")
        }
    }
}
