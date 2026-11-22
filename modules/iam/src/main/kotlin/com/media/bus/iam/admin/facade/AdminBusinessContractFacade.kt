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

/** 어드민 매니저 계약 조회 Facade */
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

    /** 특정 매니저의 계약 목록을 매니저 정보와 함께 페이지 단위로 조회한다. */
    fun getContractByMemberId(memberId: UUID, page: Int, size: Int): AdminMemberContractListResponse {
        val member = memberService.findByMemberId(memberId.toString())

        val rawPage = reservationAdminPort.getContractsByMember(memberId, page, size)
        val items = (rawPage.items ?: emptyList()).map { AdminBusinessContractResponse.from(it) }

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
