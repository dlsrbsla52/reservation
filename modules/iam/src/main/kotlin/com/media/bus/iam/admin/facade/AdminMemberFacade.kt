package com.media.bus.iam.admin.facade

import com.media.bus.common.web.wrapper.PageResult
import com.media.bus.iam.admin.dto.*
import com.media.bus.iam.admin.service.AdminCommissionService
import com.media.bus.iam.admin.service.AdminMemberService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

/**
 * ## 어드민 멤버 관리 Facade
 *
 * 멤버 생성 시 커미션 초기화를 원자적으로 처리하기 위해
 * [AdminMemberService]와 [AdminCommissionService]를 조율한다.
 *
 * `createAdminMember`에서 두 서비스를 동일 트랜잭션으로 묶어
 * 멤버 생성과 커미션 초기화가 함께 롤백되도록 보장한다.
 */
@Service
class AdminMemberFacade(
    private val adminMemberService: AdminMemberService,
    private val commissionService: AdminCommissionService,
) {

    /**
     * 어드민 멤버를 생성하고 기본 커미션(10%)을 초기화한다.
     *
     * ADMIN 계열 타입(`isAdmin == true`)은 영업사원 권한을 획득할 수 있으므로
     * 생성 시점에 `manager_commission` 레코드를 10% 기본값으로 함께 생성한다.
     */
    @Transactional
    fun createAdminMember(request: CreateAdminMemberRequest): AdminMemberResponse {
        val response = adminMemberService.createAdminMember(request)
        commissionService.initManagerCommission(response.memberId)
        return response
    }

    fun findAllMembers(page: Int, size: Int): PageResult<AdminMemberListResponse> =
        adminMemberService.findAllMembers(page, size)

    fun findMemberDetail(memberId: UUID): AdminMemberDetailResponse =
        adminMemberService.findMemberDetail(memberId)

    fun suspendMember(requesterId: UUID, targetMemberId: UUID, reason: String) =
        adminMemberService.suspendMember(requesterId, targetMemberId, reason)

    fun unsuspendMember(requesterId: UUID, targetMemberId: UUID, reason: String) =
        adminMemberService.unsuspendMember(requesterId, targetMemberId, reason)

    fun findMemberStatusHistory(memberId: UUID): List<MemberStatusHistoryResponse> =
        adminMemberService.findMemberStatusHistory(memberId)

    fun searchMembers(keyword: String): List<AdminMemberListResponse> =
        adminMemberService.searchMembers(keyword)
}
