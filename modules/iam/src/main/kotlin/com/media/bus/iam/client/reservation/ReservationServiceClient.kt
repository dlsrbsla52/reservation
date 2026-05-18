package com.media.bus.iam.client.reservation

import com.media.bus.iam.client.reservation.dto.AdminContractPageResponse
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
     * 호출부는 별도의 null 체크 없이 응답 구조만 그대로 신뢰할 수 있다.
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
}
