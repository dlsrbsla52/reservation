package com.media.bus.iam.admin.facade

import com.media.bus.common.web.wrapper.PageResult
import com.media.bus.iam.admin.dto.*
import com.media.bus.iam.admin.service.AdminCommissionService
import com.media.bus.iam.admin.service.AdminMemberService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

/** 어드민 멤버 관리 Facade */
@Service
class AdminMemberFacade(
    private val adminMemberService: AdminMemberService,
    private val commissionService: AdminCommissionService,
) {

    /** 어드민 멤버를 생성하고 기본 커미션(10%)을 초기화한다. */
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
