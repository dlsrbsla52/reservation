package com.media.bus.iam.auth.service

import com.media.bus.common.exceptions.BaseException
import com.media.bus.common.exceptions.BusinessException
import com.media.bus.common.exceptions.NoAuthenticationException
import com.media.bus.common.exceptions.NoAuthorizationException
import com.media.bus.common.result.type.CommonResult
import com.media.bus.contract.entity.member.MemberType
import com.media.bus.contract.security.JwtProvider
import com.media.bus.contract.security.MemberPrincipal
import com.media.bus.contract.security.TokenAudience
import com.media.bus.iam.admin.entity.MemberStatusHistoryEntity
import com.media.bus.iam.audit.AuditAction
import com.media.bus.iam.audit.AuditTargetType
import com.media.bus.iam.audit.entity.enumerated.AuditActorType
import com.media.bus.iam.audit.service.AuditLogService
import com.media.bus.iam.auth.dto.*
import com.media.bus.iam.auth.entity.MemberRoleEntity
import com.media.bus.iam.auth.guard.PasswordResetValidator
import com.media.bus.iam.auth.guard.RegisterRequestValidator
import com.media.bus.iam.auth.repository.RolePermissionRepository
import com.media.bus.iam.auth.repository.RoleRepository
import com.media.bus.iam.auth.result.AuthResult
import com.media.bus.iam.member.entity.MemberEntity
import com.media.bus.iam.member.entity.enumerated.MemberStatus
import com.media.bus.iam.member.repository.MemberRepository
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.util.*

/** 회원 인증/인가 비즈니스 로직 서비스 */
@Service
class AuthService(
    private val memberRepository: MemberRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtProvider: JwtProvider,
    private val redisTemplate: StringRedisTemplate,
    private val rolePermissionRepository: RolePermissionRepository,
    private val roleRepository: RoleRepository,
    private val registerRequestValidator: RegisterRequestValidator,
    private val passwordResetValidator: PasswordResetValidator,
    private val roleResolutionService: RoleResolutionService,
    private val auditLogService: AuditLogService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val EMAIL_VERIFY_KEY_PREFIX = "email-verify:"
        private val EMAIL_VERIFY_TTL = Duration.ofHours(24)

        private const val MEMBER_VERIFY_KEY_PREFIX = "member-verify:"
        private val MEMBER_VERIFY_TTL = Duration.ofMinutes(5)

        private const val PASSWORD_RESET_KEY_PREFIX = "password-reset:"
        private val PASSWORD_RESET_TTL = Duration.ofHours(1)
    }

    /** 회원가입 처리. */
    @Transactional
    fun register(request: RegisterRequest) {
        registerRequestValidator.validate(request)

        val member = MemberEntity.create(
            loginId = request.loginId,
            encodedPassword = passwordEncoder.encode(request.password)!!,
            email = request.email,
            phoneNumber = request.phoneNumber,
            businessNumber = request.businessNumber,
            memberName = request.memberName,
        )

        val role = roleRepository.findByName(request.memberType.name)
            ?: throw BaseException(AuthResult.ROLE_NOT_FOUND)
        MemberRoleEntity.create(member, role)

        val verifyToken = UUID.randomUUID().toString()
        redisTemplate.opsForValue().set(
            EMAIL_VERIFY_KEY_PREFIX + verifyToken,
            member.id.value.toString(),
            EMAIL_VERIFY_TTL,
        )

        log.info("[AuthService.register] 회원가입 완료. memberId={}, email={}", member.id.value, member.email)
        log.debug("[개발용] 이메일 인증 토큰: {}", verifyToken)
    }

    /** 로그인 처리. */
    @Transactional(readOnly = true)
    fun login(
        request: LoginRequest,
        deviceInfo: String? = null,
        ip: String? = null,
        audience: TokenAudience = TokenAudience.USER,
    ): AuthTokenResult {
        val member = memberRepository.findByLoginId(request.loginId)
            ?: throw NoAuthenticationException(CommonResult.BAD_CREDENTIAL_FAIL)

        if (!passwordEncoder.matches(request.password, member.password)) {
            throw NoAuthenticationException(CommonResult.BAD_CREDENTIAL_FAIL)
        }

        when (member.status) {
            MemberStatus.INACTIVE -> throw NoAuthenticationException(CommonResult.ACCOUNT_INACTIVE_FAIL)
            MemberStatus.SUSPENDED -> throw NoAuthenticationException(CommonResult.ACCOUNT_SUSPENDED_FAIL)
            MemberStatus.WITHDRAWN -> throw NoAuthenticationException(CommonResult.ACCOUNT_WITHDRAWN_FAIL)
            MemberStatus.ACTIVE -> { }
        }

        val memberType = roleResolutionService.resolveMemberType(member.id.value)
        validateLoginAudience(memberType, audience)

        val principal = MemberPrincipal(
            id = member.id.value,
            loginId = member.loginId,
            email = member.email,
            memberType = memberType,
            emailVerified = member.emailVerified,
            permissions = emptySet(),
        )

        val permissionNames = rolePermissionRepository.findPermissionNamesByRoleName(memberType.name)

        val accessToken = jwtProvider.generateAccessToken(principal, permissionNames, audience)
        val sessionId = UUID.randomUUID().toString()
        val refreshToken = jwtProvider.generateRefreshToken(
            memberId = member.id.value.toString(),
            sessionId = sessionId,
            deviceInfo = deviceInfo,
            ip = ip,
            audience = audience,
        )

        log.info(
            "[AuthService.login] 로그인 성공. memberId={}, sessionId={}, audience={}",
            member.id.value, sessionId, audience,
        )
        auditLogService.success(
            actorId = member.id.value,
            actorType = resolveActorType(memberType),
            action = AuditAction.LOGIN,
            targetType = AuditTargetType.MEMBER,
            targetId = member.id.value.toString(),
            detail = """{"sessionId":"$sessionId","audience":"${audience.name}"}""",
        )
        return AuthTokenResult(accessToken, refreshToken)
    }

    /** 이메일 인증 처리. */
    @Transactional
    fun verifyEmail(token: String) {
        val memberId = redisTemplate.opsForValue().get(EMAIL_VERIFY_KEY_PREFIX + token)
            ?: throw NoAuthenticationException(CommonResult.EMAIL_TOKEN_INVALID_FAIL)

        val member = memberRepository.findById(UUID.fromString(memberId))
            ?: throw NoAuthenticationException(CommonResult.USER_NOT_FOUND_FAIL)

        member.verifyEmail()
        redisTemplate.delete(EMAIL_VERIFY_KEY_PREFIX + token)

        log.info("[AuthService.verifyEmail] 이메일 인증 완료. memberId={}", memberId)
    }

    /** Refresh Token을 검증하고 새로운 Access Token을 발급한다. */
    @Transactional(readOnly = true)
    fun refreshAccessToken(refreshToken: String, expectedAudience: TokenAudience): AuthTokenResult {
        val claims = jwtProvider.tryParseClaims(refreshToken)
            ?: throw NoAuthenticationException(CommonResult.ACCESS_TOKEN_EXPIRED_FAIL)
        val audience = TokenAudience.fromClaims(claims)
        if (audience != expectedAudience) {
            throw NoAuthenticationException(AuthResult.TOKEN_AUDIENCE_MISMATCH)
        }
        val memberId = claims.subject
        val sessionId = claims[JwtProvider.SESSION_ID_CLAIM] as? String

        val valid = if (sessionId != null) {
            jwtProvider.validateRefreshToken(memberId, sessionId, refreshToken)
        } else {
            jwtProvider.validateRefreshToken(memberId, refreshToken)
        }
        if (!valid) {
            throw NoAuthenticationException(CommonResult.ACCESS_TOKEN_VERIFICATION_FAIL)
        }

        val member = memberRepository.findById(UUID.fromString(memberId))
            ?: throw NoAuthenticationException(CommonResult.USER_NOT_FOUND_FAIL)

        val memberType = roleResolutionService.resolveMemberType(member.id.value)
        if (!isAudienceAllowed(memberType, audience)) {
            sessionId?.let { jwtProvider.deleteSession(memberId, it) }
            throw NoAuthenticationException(AuthResult.TOKEN_AUDIENCE_MISMATCH)
        }

        val principal = MemberPrincipal(
            id = member.id.value,
            loginId = member.loginId,
            email = member.email,
            memberType = memberType,
            emailVerified = member.emailVerified,
            permissions = emptySet(),
        )

        val permissionNames = rolePermissionRepository.findPermissionNamesByRoleName(memberType.name)

        val newAccessToken = jwtProvider.generateAccessToken(principal, permissionNames, audience)
        val newRefreshToken = jwtProvider.generateRefreshToken(
            memberId = memberId,
            sessionId = sessionId ?: UUID.randomUUID().toString(),
            deviceInfo = null,
            ip = null,
            audience = audience,
        )

        return AuthTokenResult(newAccessToken, newRefreshToken)
    }

    /** 2차 본인 인증. */
    @Transactional(readOnly = true)
    fun verifyMember(memberId: String, request: VerifyMemberRequest) {
        val member = memberRepository.findById(UUID.fromString(memberId))
            ?: throw NoAuthenticationException(CommonResult.USER_NOT_FOUND_FAIL)

        if (!passwordEncoder.matches(request.password, member.password)) {
            throw NoAuthenticationException(CommonResult.AUTHENTICATION_FAIL)
        }

        redisTemplate.opsForValue().set(
            MEMBER_VERIFY_KEY_PREFIX + memberId,
            "verified",
            MEMBER_VERIFY_TTL,
        )

        log.info("[AuthService.verifyMember] 2차 본인 인증 성공. memberId={}", memberId)
    }

    /** 2차 본인 인증 완료 여부를 확인한다. */
    fun checkVerified(memberId: String) {
        val verified = redisTemplate.opsForValue().get(MEMBER_VERIFY_KEY_PREFIX + memberId)
        if (verified == null) {
            throw NoAuthenticationException(AuthResult.VERIFY_REQUIRED)
        }
    }

    /** 2차 본인 인증 상태를 Redis에서 삭제한다. */
    fun clearVerification(memberId: String) {
        redisTemplate.delete(MEMBER_VERIFY_KEY_PREFIX + memberId)
    }

    /** MemberType 이름 접두사로 감사 로그 행위자 유형을 구분한다 (ADMIN_* → ADMIN, 그 외 → MEMBER). */
    private fun resolveActorType(memberType: com.media.bus.contract.entity.member.MemberType): AuditActorType =
        if (memberType.name.startsWith("ADMIN")) AuditActorType.ADMIN else AuditActorType.MEMBER

    /** 로그아웃 처리. */
    fun logout(memberId: String, sessionId: String?) {
        if (sessionId != null) {
            jwtProvider.deleteSession(memberId, sessionId)
            log.info("[AuthService.logout] 세션 로그아웃 처리. memberId={}, sessionId={}", memberId, sessionId)
        } else {
            log.info("[AuthService.logout] refresh 쿠키 없음 — 세션 삭제 없이 쿠키만 만료. memberId={}", memberId)
        }
        auditLogService.success(
            actorId = runCatching { UUID.fromString(memberId) }.getOrNull(),
            actorType = AuditActorType.MEMBER,
            action = AuditAction.LOGOUT,
            targetType = AuditTargetType.SESSION,
            targetId = sessionId,
        )
    }

    /** 비밀번호 초기화 요청. */
    fun requestPasswordReset(request: PasswordResetRequest) {
        passwordResetValidator.validate(request)

        val member = request.loginId?.let { memberRepository.findByLoginId(it) }
            ?: request.email?.let { memberRepository.findByEmail(it) }

        if (member == null || member.status != MemberStatus.ACTIVE) {
            log.debug("[AuthService.requestPasswordReset] 회원 조회 실패 또는 비활성 상태. 조용히 반환.")
            return
        }

        val resetToken = UUID.randomUUID().toString()
        redisTemplate.opsForValue().set(
            PASSWORD_RESET_KEY_PREFIX + resetToken,
            member.id.value.toString(),
            PASSWORD_RESET_TTL,
        )

        log.info("[AuthService.requestPasswordReset] 비밀번호 초기화 토큰 발급. memberId={}", member.id.value)
        log.debug("[개발용] 비밀번호 초기화 토큰: {}", resetToken)
    }

    /** 비밀번호 초기화 토큰 유효성 확인. */
    fun verifyPasswordResetToken(token: String) {
        redisTemplate.opsForValue().get(PASSWORD_RESET_KEY_PREFIX + token)
            ?: throw BusinessException(AuthResult.PASSWORD_RESET_TOKEN_INVALID)
    }

    /** 로그인 상태에서 사용자가 직접 비밀번호를 변경한다. */
    @Transactional
    fun changePassword(memberId: String, request: PasswordChangeRequest) {
        val member = memberRepository.findById(UUID.fromString(memberId))
            ?: throw BaseException(AuthResult.MEMBER_NOT_FOUND)

        if (!passwordEncoder.matches(request.currentPassword, member.password)) {
            auditLogService.failure(
                actorId = member.id.value,
                actorType = AuditActorType.MEMBER,
                action = AuditAction.PASSWORD_CHANGE,
                targetType = AuditTargetType.MEMBER,
                targetId = memberId,
                detail = """{"reason":"CURRENT_PASSWORD_MISMATCH"}""",
            )
            throw NoAuthenticationException(AuthResult.CURRENT_PASSWORD_MISMATCH)
        }

        if (passwordEncoder.matches(request.newPassword, member.password)) {
            auditLogService.failure(
                actorId = member.id.value,
                actorType = AuditActorType.MEMBER,
                action = AuditAction.PASSWORD_CHANGE,
                targetType = AuditTargetType.MEMBER,
                targetId = memberId,
                detail = """{"reason":"SAME_AS_CURRENT"}""",
            )
            throw BusinessException(AuthResult.NEW_PASSWORD_SAME_AS_CURRENT)
        }

        member.changePassword(passwordEncoder.encode(request.newPassword)!!)

        jwtProvider.deleteRefreshToken(memberId)

        log.info("[AuthService.changePassword] 비밀번호 변경 완료. memberId={}", memberId)
        auditLogService.success(
            actorId = member.id.value,
            actorType = AuditActorType.MEMBER,
            action = AuditAction.PASSWORD_CHANGE,
            targetType = AuditTargetType.MEMBER,
            targetId = memberId,
        )
    }

    /** 비밀번호 초기화 확정. */
    @Transactional
    fun confirmPasswordReset(request: PasswordResetConfirmRequest) {
        val memberId = redisTemplate.opsForValue().get(PASSWORD_RESET_KEY_PREFIX + request.token)
            ?: throw BusinessException(AuthResult.PASSWORD_RESET_TOKEN_INVALID)

        val member = memberRepository.findById(UUID.fromString(memberId))
            ?: throw BaseException(AuthResult.MEMBER_NOT_FOUND)

        member.changePassword(passwordEncoder.encode(request.newPassword)!!)

        redisTemplate.delete(PASSWORD_RESET_KEY_PREFIX + request.token)
        jwtProvider.deleteRefreshToken(memberId)

        log.info("[AuthService.confirmPasswordReset] 비밀번호 초기화 완료. memberId={}", memberId)
        auditLogService.success(
            actorId = member.id.value,
            actorType = AuditActorType.ANONYMOUS,
            action = AuditAction.PASSWORD_RESET_CONFIRM,
            targetType = AuditTargetType.MEMBER,
            targetId = memberId,
        )
    }

    /** 사용자가 스스로 비활성화(INACTIVE)한 계정을 다시 활성화한다. */
    @Transactional
    fun reactivate(
        request: LoginRequest,
        deviceInfo: String? = null,
        ip: String? = null,
        audience: TokenAudience = TokenAudience.USER,
    ): AuthTokenResult {
        val member = memberRepository.findByLoginId(request.loginId)
            ?: throw NoAuthenticationException(CommonResult.BAD_CREDENTIAL_FAIL)

        if (!passwordEncoder.matches(request.password, member.password)) {
            throw NoAuthenticationException(CommonResult.BAD_CREDENTIAL_FAIL)
        }

        if (member.status != MemberStatus.INACTIVE) {
            throw BusinessException(CommonResult.ACCOUNT_NOT_INACTIVE_FAIL)
        }

        val memberType = roleResolutionService.resolveMemberType(member.id.value)
        validateLoginAudience(memberType, audience)

        val previousStatus = member.status
        member.reactivate()

        MemberStatusHistoryEntity.create(
            member = member,
            previousStatus = previousStatus,
            newStatus = MemberStatus.ACTIVE,
            reason = "사용자 본인 재활성화",
            changedBy = member,
        )

        val principal = MemberPrincipal(
            id = member.id.value,
            loginId = member.loginId,
            email = member.email,
            memberType = memberType,
            emailVerified = member.emailVerified,
            permissions = emptySet(),
        )
        val permissionNames = rolePermissionRepository.findPermissionNamesByRoleName(memberType.name)
        val accessToken = jwtProvider.generateAccessToken(principal, permissionNames, audience)
        val sessionId = UUID.randomUUID().toString()
        val refreshToken = jwtProvider.generateRefreshToken(
            memberId = member.id.value.toString(),
            sessionId = sessionId,
            deviceInfo = deviceInfo,
            ip = ip,
            audience = audience,
        )

        log.info("[AuthService.reactivate] 계정 재활성화 완료. memberId={}, sessionId={}", member.id.value, sessionId)
        auditLogService.success(
            actorId = member.id.value,
            actorType = resolveActorType(memberType),
            action = AuditAction.MEMBER_REACTIVATE,
            targetType = AuditTargetType.MEMBER,
            targetId = member.id.value.toString(),
        )
        return AuthTokenResult(accessToken, refreshToken)
    }

    /** 계정 유형과 로그인한 사이트가 맞는지 확인한다. */
    private fun validateLoginAudience(memberType: MemberType, audience: TokenAudience) {
        if (isAudienceAllowed(memberType, audience)) return
        throw when (audience) {
            TokenAudience.USER -> NoAuthorizationException(AuthResult.ADMIN_SITE_LOGIN_REQUIRED)
            TokenAudience.ADMIN -> NoAuthorizationException(AuthResult.ADMIN_ACCOUNT_REQUIRED)
        }
    }

    /** 관리자 계정 ⇔ 관리자 사이트, 그 외 계정 ⇔ 일반 사이트만 허용한다. */
    private fun isAudienceAllowed(memberType: MemberType, audience: TokenAudience): Boolean =
        memberType.isAdmin == (audience == TokenAudience.ADMIN)

    /** 특정 회원의 활성 세션 목록을 반환한다. */
    fun listMySessions(memberId: String, currentSessionId: String? = null): List<SessionResponse> =
        jwtProvider.listSessions(memberId).map { (sid, info) ->
            SessionResponse(
                sessionId = sid,
                deviceInfo = info.deviceInfo,
                ip = info.ip,
                issuedAt = info.issuedAt,
                lastAccessedAt = info.lastAccessedAt,
                current = sid == currentSessionId,
            )
        }.sortedByDescending { it.lastAccessedAt }

    /** 특정 세션을 강제 로그아웃 시킨다. 자기 자신이 보유한 세션만 revoke 할 수 있다. */
    fun revokeSession(memberId: String, sessionId: String) {
        jwtProvider.deleteSession(memberId, sessionId)
        log.info("[AuthService.revokeSession] 세션 revoke. memberId={}, sessionId={}", memberId, sessionId)
        auditLogService.success(
            actorId = runCatching { UUID.fromString(memberId) }.getOrNull(),
            actorType = AuditActorType.MEMBER,
            action = AuditAction.SESSION_REVOKE,
            targetType = AuditTargetType.SESSION,
            targetId = sessionId,
        )
    }

    /** 현재 세션을 제외한 모든 세션을 로그아웃 시킨다. */
    fun revokeOtherSessions(memberId: String, currentSessionId: String) {
        val others = jwtProvider.listSessions(memberId).keys.filter { it != currentSessionId }
        others.forEach { jwtProvider.deleteSession(memberId, it) }
        log.info(
            "[AuthService.revokeOtherSessions] 현재 세션 제외 {}개 revoke. memberId={}, current={}",
            others.size, memberId, currentSessionId,
        )
        auditLogService.success(
            actorId = runCatching { UUID.fromString(memberId) }.getOrNull(),
            actorType = AuditActorType.MEMBER,
            action = AuditAction.SESSION_REVOKE_OTHERS,
            targetType = AuditTargetType.SESSION,
            detail = """{"revokedCount":${others.size},"currentSessionId":"$currentSessionId"}""",
        )
    }
}
