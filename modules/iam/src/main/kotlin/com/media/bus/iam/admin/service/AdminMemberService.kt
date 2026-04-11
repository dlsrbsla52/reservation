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

/**
 * ## 어드민 멤버 생성 비즈니스 로직 서비스
 *
 * 일반 가입(`AuthService.register`)과의 차이점:
 * - `emailVerified = true`로 즉시 설정 (manager가 직접 생성하므로 인증 불필요)
 * - 이메일 인증 토큰 Redis 저장 생략
 * - ADMIN 계열 타입만 허용 (`AdminRegisterRequestValidator` 위임)
 */
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
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 어드민 멤버를 생성한다.
     * 1. 입력 검증 (`AdminRegisterRequestValidator`에 위임)
     * 2. 이메일 인증 완료 상태로 Member 저장
     * 3. 역할(Role) 조회 및 MemberRole 저장
     */
    @Transactional
    fun createAdminMember(request: CreateAdminMemberRequest): AdminMemberResponse {
        // 입력 검증
        adminRegisterRequestValidator.validate(request)

        // 어드민은 manager가 직접 생성하므로 emailVerified = true로 설정
        val member = MemberEntity.createAdmin(
            loginId = request.loginId,
            encodedPassword = passwordEncoder.encode(request.password)!!,
            email = request.email,
            phoneNumber = request.phoneNumber,
            memberName = request.memberName,
        )

        // role 마스터 테이블에서 MemberType에 해당하는 Role 조회 및 역할 부여
        val role = roleRepository.findByName(request.memberType.name)
            ?: throw BaseException(AuthResult.ROLE_NOT_FOUND)
        MemberRoleEntity.of(member, role)

        log.info(
            "[AdminMemberService.createAdminMember] 어드민 멤버 생성 완료. memberId={}, memberType={}",
            member.id.value, request.memberType,
        )

        return AdminMemberResponse.of(member, request.memberType)
    }

    // ─────────────────────────────────────────────────────────────────
    // 회원 관리 (조회, 검색, 정지, 해제)
    // ─────────────────────────────────────────────────────────────────

    /** 전체 회원 목록을 페이지네이션으로 조회한다. */
    @Transactional(readOnly = true)
    fun findAllMembers(page: Int, size: Int): PageResult<AdminMemberListResponse> {
        val members = memberRepository.findAllPaged(page, size)
        val totalCnt = memberRepository.count()

        val items = members.map { member ->
            val memberType = roleResolutionService.resolveMemberType(member.id.value)
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

    /**
     * 회원 계정을 정지한다.
     * - 자기 자신 정지 불가
     * - ADMIN_MASTER 정지 불가
     * - ACTIVE 상태만 정지 가능
     * - 정지 후 Refresh Token 무효화
     * - 정지 이력 저장 (사유, 처리자)
     */
    @Transactional
    fun suspendMember(requesterId: UUID, targetMemberId: UUID, reason: String) {
        if (requesterId == targetMemberId) {
            throw BusinessException(AuthResult.CANNOT_SUSPEND_SELF)
        }

        val member = memberRepository.findById(targetMemberId)
            ?: throw BaseException(AuthResult.MEMBER_NOT_FOUND)

        if (member.status != MemberStatus.ACTIVE) {
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

        // 상태 변경 이력 저장
        MemberStatusHistoryEntity.create(
            member = member,
            previousStatus = previousStatus,
            newStatus = MemberStatus.SUSPENDED,
            reason = reason,
            changedBy = requester,
        )

        jwtProvider.deleteRefreshToken(targetMemberId.toString())

        log.info("[AdminMemberService.suspendMember] 회원 정지 완료. targetMemberId={}, reason={}", targetMemberId, reason)
    }

    /**
     * 정지된 회원의 계정을 해제한다.
     * SUSPENDED 상태만 해제 가능하며, 해제 이력도 저장한다.
     */
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

        // 상태 변경 이력 저장
        MemberStatusHistoryEntity.create(
            member = member,
            previousStatus = previousStatus,
            newStatus = MemberStatus.ACTIVE,
            reason = reason,
            changedBy = requester,
        )

        log.info("[AdminMemberService.unsuspendMember] 회원 정지 해제 완료. targetMemberId={}, reason={}", targetMemberId, reason)
    }

    /** 특정 회원의 상태 변경 이력을 조회한다. */
    @Transactional(readOnly = true)
    fun findMemberStatusHistory(memberId: UUID): List<MemberStatusHistoryResponse> {
        memberRepository.findById(memberId)
            ?: throw BaseException(AuthResult.MEMBER_NOT_FOUND)

        return memberStatusHistoryRepository.findByMemberId(memberId)
            .map { MemberStatusHistoryResponse.of(it) }
    }

    /** 키워드로 회원을 검색한다 (loginId, email, memberName LIKE 검색). */
    @Transactional(readOnly = true)
    fun searchMembers(keyword: String): List<AdminMemberListResponse> {
        val members = memberRepository.searchByKeyword(keyword)
        return members.map { member ->
            val memberType = roleResolutionService.resolveMemberType(member.id.value)
            AdminMemberListResponse.of(member, memberType)
        }
    }
}
