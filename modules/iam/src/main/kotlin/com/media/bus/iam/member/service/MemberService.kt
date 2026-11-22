package com.media.bus.iam.member.service

import com.media.bus.common.exceptions.BusinessException
import com.media.bus.common.result.type.CommonResult
import com.media.bus.contract.security.JwtProvider
import com.media.bus.iam.admin.entity.MemberStatusHistoryEntity
import com.media.bus.iam.audit.AuditAction
import com.media.bus.iam.audit.AuditTargetType
import com.media.bus.iam.audit.entity.enumerated.AuditActorType
import com.media.bus.iam.audit.service.AuditLogService
import com.media.bus.iam.auth.result.AuthResult
import com.media.bus.iam.auth.service.AuthService
import com.media.bus.iam.auth.service.RoleResolutionService
import com.media.bus.iam.member.dto.FindLoginIdResponse
import com.media.bus.iam.member.dto.FindMeRequest
import com.media.bus.iam.member.dto.MemberModifyRequest
import com.media.bus.iam.member.dto.MemberResponse
import com.media.bus.iam.member.entity.MemberEntity
import com.media.bus.iam.member.entity.enumerated.MemberStatus
import com.media.bus.iam.member.repository.MemberRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class MemberService(
    private val memberRepository: MemberRepository,
    private val jwtProvider: JwtProvider,
    private val roleResolutionService: RoleResolutionService,
    private val authService: AuthService,
    private val auditLogService: AuditLogService,
) {
    private val log = LoggerFactory.getLogger(MemberService::class.java)

    /** 로그인시 사용된 jwt 토큰으로 회원 조회. */
    @Transactional(readOnly = true)
    fun findByJwtMember(jwt: String): MemberResponse {
        val principal = jwtProvider.getPrincipalFromClaims(
            jwtProvider.parseClaimsFromToken(jwt),
        )

        log.debug("요청 ID : {}", principal.id)
        val member = memberRepository.findById(principal.id)
            ?: throw BusinessException(CommonResult.USER_NOT_FOUND_FAIL)
        return toResponse(member)
    }

    /** memberId를 통한 회원 조회. */
    @Transactional(readOnly = true)
    fun findByMemberId(memberId: String): MemberResponse {
        val member = memberRepository.findById(UUID.fromString(memberId))
            ?: throw BusinessException(CommonResult.USER_NOT_FOUND_FAIL)
        return toResponse(member)
    }

    /** 로그인 아이디를 통한 회원 조회. */
    @Transactional(readOnly = true)
    fun findByLoginId(loginId: String): MemberResponse {
        val member = memberRepository.findByLoginId(loginId)
            ?: throw BusinessException(CommonResult.USER_NOT_FOUND_FAIL)
        return toResponse(member)
    }

    /** 이메일을 통한 회원 조회. */
    @Transactional(readOnly = true)
    fun findByEmail(email: String): MemberResponse {
        val member = memberRepository.findByEmail(email)
            ?: throw BusinessException(CommonResult.USER_NOT_FOUND_FAIL)
        return toResponse(member)
    }

    /** 회원이 아이디를 분실했을 경우 처리. */
    @Transactional(readOnly = true)
    fun findMe(request: FindMeRequest): FindLoginIdResponse {
        if (request.memberName.isBlank() || (request.phoneNumber.isNullOrBlank() && request.email.isNullOrBlank())) {
            throw BusinessException(AuthResult.FIND_ME_KEY_REQUIRED)
        }

        val member = memberRepository.findMe(request)
            ?: throw BusinessException(CommonResult.USER_NOT_FOUND_FAIL)

        return FindLoginIdResponse(loginId = member.loginId)
    }

    /** 회원 탈퇴 처리. */
    @Transactional
    fun withdraw(memberId: String) {
        authService.checkVerified(memberId)

        val member = memberRepository.findById(UUID.fromString(memberId))
            ?: throw BusinessException(CommonResult.USER_NOT_FOUND_FAIL)

        member.withdraw()
        authService.clearVerification(memberId)
        log.debug("[MemberService.withdraw] 회원 탈퇴 처리 완료. memberId={}", memberId)
        auditLogService.success(
            actorId = member.id.value,
            actorType = AuditActorType.MEMBER,
            action = AuditAction.MEMBER_WITHDRAW,
            targetType = AuditTargetType.MEMBER,
            targetId = memberId,
        )
    }

    fun modify(memberId: String, request: MemberModifyRequest) {
        authService.checkVerified(memberId)

        val member = memberRepository.findById(UUID.fromString(memberId))
            ?: throw BusinessException(CommonResult.USER_NOT_FOUND_FAIL)

        member.modify(request)
        log.debug("[MemberService.modify] 회원 정보 수정 완료. memberId={}", memberId)
        auditLogService.success(
            actorId = member.id.value,
            actorType = AuditActorType.MEMBER,
            action = AuditAction.MEMBER_MODIFY,
            targetType = AuditTargetType.MEMBER,
            targetId = memberId,
        )
    }

    /** 사용자가 스스로 계정을 일시 비활성화한다. */
    @Transactional
    fun deactivate(memberId: String, reason: String) {
        authService.checkVerified(memberId)

        val member = memberRepository.findById(UUID.fromString(memberId))
            ?: throw BusinessException(CommonResult.USER_NOT_FOUND_FAIL)

        if (member.status != MemberStatus.ACTIVE) {
            throw BusinessException(com.media.bus.iam.auth.result.AuthResult.MEMBER_NOT_ACTIVE)
        }

        val previousStatus = member.status
        member.deactivate()

        MemberStatusHistoryEntity.create(
            member = member,
            previousStatus = previousStatus,
            newStatus = MemberStatus.INACTIVE,
            reason = reason.ifBlank { "사용자 본인 비활성화 요청" },
            changedBy = member,
        )

        jwtProvider.deleteRefreshToken(memberId)
        authService.clearVerification(memberId)

        log.info("[MemberService.deactivate] 계정 비활성화 완료. memberId={}", memberId)
        auditLogService.success(
            actorId = member.id.value,
            actorType = AuditActorType.MEMBER,
            action = AuditAction.MEMBER_DEACTIVATE,
            targetType = AuditTargetType.MEMBER,
            targetId = memberId,
            detail = """{"reason":${jsonStringOrNull(reason)}}""",
        )
    }

    /** 단순 JSON 문자열 이스케이프. null이면 null 리터럴, 아니면 큰따옴표로 감싸 이스케이프한다. */
    private fun jsonStringOrNull(value: String?): String =
        if (value.isNullOrBlank()) "null"
        else "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

    /** Member 엔티티를 MemberResponse로 변환한다. */
    private fun toResponse(member: MemberEntity): MemberResponse {
        val memberType = roleResolutionService.resolveMemberType(member.id.value)
        return MemberResponse(
            id = member.id.value,
            loginId = member.loginId,
            email = member.email,
            phoneNumber = member.phoneNumber,
            memberType = memberType,
            status = member.status,
            businessNumber = member.businessNumber,
            createdAt = member.createdAt,
            updatedAt = member.updatedAt,
        )
    }
}
