package com.media.bus.iam.admin.facade

import com.media.bus.common.web.wrapper.PageResult
import com.media.bus.iam.admin.dto.AdminBusinessContractResponse
import com.media.bus.iam.admin.dto.AdminContractResponse
import com.media.bus.iam.admin.dto.AdminMemberContractListResponse
import com.media.bus.iam.admin.dto.CreateAdminContractRequest
import com.media.bus.iam.member.service.MemberService
import com.media.bus.iam.port.ReservationAdminPort
import com.media.bus.iam.port.dto.reservation.CreateContractRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import java.util.*

/**
 * ## 어드민 매니저 계약 조회 Facade
 *
 * 설계 의도:
 * - 어드민은 모든 매니저의 계약을 열람할 수 있어야 하므로 `ReservationAdminPort`로 reservation 모듈을 호출한다.
 * - 매니저 기본 정보는 iam DB에서 즉시 조회하여 모듈 간 데이터 결합 책임을 이 Facade가 담당한다.
 * - 컨트롤러 단의 `@Authorize`가 ADMIN_MASTER/ADMIN_DEVELOPER + MANAGE 권한 검증을 마쳤음을 전제로 한다.
 *
 * 흐름:
 * 1단계: `MemberService.findByMemberId` — iam DB에서 매니저 정보 조회(존재하지 않으면 404).
 * 2단계: `ReservationAdminPort.getContractsByMember` — reservation 모듈 조회(정류소 정보 결합 완료 상태).
 * 3단계: 응답 DTO로 변환하여 매니저 요약 + 계약 페이지를 함께 반환.
 *
 * 사이드 이펙트 / 운영 고려사항:
 * - 계약 목록이 비면 빈 페이지로 응답한다(페이지 메타가 비면 요청값으로 채움) — 매니저 컨텍스트는 그대로 노출.
 *   reservation 조회 예외는 삼키지 않고 전파한다(어댑터가 예전 S2S 경로와 같은 상태·코드로 바꾼다).
 * - 매니저 미존재 시 `MemberService`가 `BusinessException(USER_NOT_FOUND_FAIL)`을 던지므로
 *   글로벌 `ExceptionAdvisor`가 일관된 404를 응답한다.
 */
@Service
class AdminBusinessContractFacade(
    private val reservationAdminPort: ReservationAdminPort,
    private val memberService: MemberService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 전체 계약 검색 결과를 IAM 공개 응답으로 변환한다. */
    fun searchContracts(
        memberId: UUID?,
        stopId: UUID?,
        status: String?,
        keyword: String?,
        contractStartFrom: OffsetDateTime?,
        contractStartTo: OffsetDateTime?,
        page: Int,
        size: Int,
    ): PageResult<AdminBusinessContractResponse> {
        val raw = reservationAdminPort.searchContracts(
            memberId, stopId, status, keyword, contractStartFrom, contractStartTo, page, size,
        )
        val items = raw.items.orEmpty().map(AdminBusinessContractResponse::from)
        return PageResult(
            items = items,
            totalCnt = raw.totalCnt ?: items.size.toLong(),
            pageRows = raw.pageRows ?: size,
            pageNum = raw.pageNum ?: page,
        )
    }

    /** `ReservationAdminPort`로 관리자 계약을 생성한다. */
    fun createContract(request: CreateAdminContractRequest): AdminContractResponse =
        AdminContractResponse.from(
            reservationAdminPort.createContract(CreateContractRequest.from(request)),
        )

    /** `ReservationAdminPort`로 계약 상세를 조회한다. */
    fun getContractDetail(contractId: UUID): AdminBusinessContractResponse =
        AdminBusinessContractResponse.from(reservationAdminPort.getContractDetail(contractId))

    /**
     * 특정 매니저의 계약 목록을 매니저 정보와 함께 페이지 단위로 조회한다.
     *
     * @param memberId 매니저 회원 ID(UUID)
     * @param page     0-base 페이지 번호
     * @param size     페이지 크기
     */
    fun getContractByMemberId(memberId: UUID, page: Int, size: Int): AdminMemberContractListResponse {
        // 1단계: 매니저 정보 조회 (없으면 BusinessException 으로 404)
        val member = memberService.findByMemberId(memberId.toString())

        // 2단계: reservation 모듈 조회 (정류소 정보가 결합된 어드민 view 페이지 수신)
        val rawPage = reservationAdminPort.getContractsByMember(memberId, page, size)
        val items = (rawPage.items ?: emptyList()).map { AdminBusinessContractResponse.from(it) }

        // 3단계: 응답 DTO 결합
        val contracts = PageResult(
            items = items,
            totalCnt = rawPage.totalCnt ?: items.size.toLong(),
            pageRows = rawPage.pageRows ?: size,
            pageNum = rawPage.pageNum ?: page,
        )
        log.debug(
            "[AdminBusinessContractFacade] 매니저 계약 결합 완료: memberId={}, page={}, size={}, count={}, total={}",
            memberId, page, size, items.size, contracts.totalCnt,
        )
        return AdminMemberContractListResponse.of(member, contracts)
    }
}
