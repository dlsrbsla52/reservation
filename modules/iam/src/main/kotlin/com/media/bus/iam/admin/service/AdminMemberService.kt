package com.media.bus.iam.admin.service

import com.media.bus.common.exceptions.BaseException
import com.media.bus.common.exceptions.BusinessException
import com.media.bus.common.web.wrapper.PageResult
import com.media.bus.contract.entity.member.MemberType
import com.media.bus.contract.security.JwtProvider
import com.media.bus.iam.admin.dto.*
import com.media.bus.iam.admin.entity.MemberStatusHistoryEntity
import com.media.bus.iam.admin.guard.AdminRegisterRequestValidator
import com.media.bus.iam.admin.repository.MemberStatusHistoryRepository
import com.media.bus.iam.audit.AuditAction
import com.media.bus.iam.audit.AuditTargetType
import com.media.bus.iam.audit.entity.enumerated.AuditActorType
import com.media.bus.iam.audit.service.AuditLogService
import com.media.bus.iam.auth.entity.MemberRoleEntity
import com.media.bus.iam.auth.repository.MemberRoleRepository
import com.media.bus.iam.auth.repository.RolePermissionRepository
import com.media.bus.iam.auth.repository.RoleRepository
import com.media.bus.iam.auth.result.AuthResult
import com.media.bus.iam.auth.service.RoleResolutionService
import com.media.bus.iam.member.entity.MemberEntity
import com.media.bus.iam.member.entity.enumerated.MemberStatus
import com.media.bus.iam.member.repository.MemberRepository
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

/** 어드민 멤버 생성 비즈니스 로직 서비스 */
@Service
class AdminMemberService(
    private val roleRepository: RoleRepository,
    private val passwordEncoder: PasswordEncoder,
    private val adminRegisterRequestValidator: AdminRegisterRequestValidator,
    private val memberRepository: MemberRepository,
    private val roleResolutionService: RoleResolutionService,
    private val jwtProvider: JwtProvider,
    private val memberRoleRepository: MemberRoleRepository,
    private val rolePermissionRepository: RolePermissionRepository,
    private val memberStatusHistoryRepository: MemberStatusHistoryRepository,
    private val auditLogService: AuditLogService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 어드민 멤버를 생성한다. */
    @Transactional
    fun createAdminMember(request: CreateAdminMemberRequest): AdminMemberResponse {
        adminRegisterRequestValidator.validate(request)

        val member = MemberEntity.createAdmin(
            loginId = request.loginId,
            encodedPassword = passwordEncoder.encode(request.password)!!,
            email = request.email,
            phoneNumber = request.phoneNumber,
            memberName = request.memberName,
        )

        val role = roleRepository.findByName(request.memberType.name)
            ?: throw BaseException(AuthResult.ROLE_NOT_FOUND)
        MemberRoleEntity.create(member, role)

        log.info(
            "[AdminMemberService.createAdminMember] 어드민 멤버 생성 완료. memberId={}, memberType={}",
            member.id.value, request.memberType,
        )
        auditLogService.success(
            actorId = null,
            actorType = AuditActorType.ADMIN,
            action = AuditAction.ADMIN_CREATE,
            targetType = AuditTargetType.MEMBER,
            targetId = member.id.value.toString(),
            detail = """{"memberType":"${request.memberType.name}"}""",
        )

        return AdminMemberResponse.of(member, request.memberType)
    }

    /** 전체 회원 목록을 페이지네이션으로 조회한다. */
    @Transactional(readOnly = true)
    fun findAllMembers(page: Int, size: Int): PageResult<AdminMemberListResponse> {
        val members = memberRepository.findAllPaged(page, size)
        val totalCnt = memberRepository.count()

        val memberTypeMap = roleResolutionService.resolveTypesForMembers(members.map { it.id.value })
        val items = members.map { member ->
            val memberType = memberTypeMap[member.id.value]
                ?: throw BusinessException(AuthResult.ROLE_NOT_FOUND)
            AdminMemberListResponse.of(member, memberType)
        }

        return PageResult(
            items = items,
            totalCnt = totalCnt,
            pageRows = size,
            pageNum = page,
        )
    }

    /** 회원 상세 정보를 조회한다 (역할 및 권한 포함). */
    @Transactional(readOnly = true)
    fun findMemberDetail(memberId: UUID): AdminMemberDetailResponse {
        val member = memberRepository.findById(memberId)
            ?: throw BaseException(AuthResult.MEMBER_NOT_FOUND)

        val memberRole = memberRoleRepository.findByMemberId(memberId)
            ?: throw BaseException(AuthResult.MEMBER_ROLE_NOT_FOUND)

        val role = memberRole.role
        val memberType = MemberType.valueOf(role.name)
        val permissions = rolePermissionRepository.findByRoleId(role.id.value)
            .map { it.permission }

        return AdminMemberDetailResponse.of(member, memberType, role, permissions)
    }

    /** 회원 계정을 정지한다. */
    @Transactional
    fun suspendMember(requesterId: UUID, targetMemberId: UUID, reason: String) {
        if (requesterId == targetMemberId) {
            throw BusinessException(AuthResult.CANNOT_SUSPEND_SELF)
        }

        val member = memberRepository.findById(targetMemberId)
            ?: throw BaseException(AuthResult.MEMBER_NOT_FOUND)

        if (member.status != MemberStatus.ACTIVE && member.status != MemberStatus.INACTIVE) {
            throw BusinessException(AuthResult.MEMBER_NOT_ACTIVE)
        }

        val memberType = roleResolutionService.resolveMemberType(targetMemberId)
        if (memberType == MemberType.ADMIN_MASTER) {
            throw BusinessException(AuthResult.CANNOT_SUSPEND_ADMIN_MASTER)
        }

        val requester = memberRepository.findById(requesterId)
            ?: throw BaseException(AuthResult.MEMBER_NOT_FOUND)

        val previousStatus = member.status
        member.suspend()

        MemberStatusHistoryEntity.create(
            member = member,
            previousStatus = previousStatus,
            newStatus = MemberStatus.SUSPENDED,
            reason = reason,
            changedBy = requester,
        )

        jwtProvider.deleteRefreshToken(targetMemberId.toString())

        log.info("[AdminMemberService.suspendMember] 회원 정지 완료. targetMemberId={}, reason={}", targetMemberId, reason)
        auditLogService.success(
            actorId = requesterId,
            actorType = AuditActorType.ADMIN,
            action = AuditAction.MEMBER_SUSPEND,
            targetType = AuditTargetType.MEMBER,
            targetId = targetMemberId.toString(),
            detail = """{"reason":${escapeJsonString(reason)}}""",
        )
    }

    /** 정지된 회원의 계정을 해제한다. */
    @Transactional
    fun unsuspendMember(requesterId: UUID, targetMemberId: UUID, reason: String) {
        val member = memberRepository.findById(targetMemberId)
            ?: throw BaseException(AuthResult.MEMBER_NOT_FOUND)

        if (member.status != MemberStatus.SUSPENDED) {
            throw BusinessException(AuthResult.MEMBER_NOT_SUSPENDED)
        }

        val requester = memberRepository.findById(requesterId)
            ?: throw BaseException(AuthResult.MEMBER_NOT_FOUND)

        val previousStatus = member.status
        member.unsuspend()

        MemberStatusHistoryEntity.create(
            member = member,
            previousStatus = previousStatus,
            newStatus = MemberStatus.ACTIVE,
            reason = reason,
            changedBy = requester,
        )

        log.info("[AdminMemberService.unsuspendMember] 회원 정지 해제 완료. targetMemberId={}, reason={}", targetMemberId, reason)
        auditLogService.success(
            actorId = requesterId,
            actorType = AuditActorType.ADMIN,
            action = AuditAction.MEMBER_UNSUSPEND,
            targetType = AuditTargetType.MEMBER,
            targetId = targetMemberId.toString(),
            detail = """{"reason":${escapeJsonString(reason)}}""",
        )
    }

    /** 단순 JSON 문자열 이스케이프 — detail 필드에 사용. */
    private fun escapeJsonString(value: String): String =
        "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

    /** 특정 회원의 상태 변경 이력을 조회한다. */
    @Transactional(readOnly = true)
    fun findMemberStatusHistory(memberId: UUID): List<MemberStatusHistoryResponse> {
        memberRepository.findById(memberId)
            ?: throw BaseException(AuthResult.MEMBER_NOT_FOUND)

        return memberStatusHistoryRepository.findByMemberId(memberId)
            .map { MemberStatusHistoryResponse.of(it) }
    }

    /** 키워드로 회원을 검색한다 (loginId, email, memberName 전방 일치 검색). */
    @Transactional(readOnly = true)
    fun searchMembers(keyword: String): List<AdminMemberListResponse> =
        memberRepository.searchByKeyword(keyword).map {
            val memberType = MemberType.valueOf(it.roleName)
            AdminMemberListResponse.of(it.member, memberType)
        }
}
