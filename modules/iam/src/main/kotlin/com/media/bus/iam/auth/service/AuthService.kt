package com.media.bus.iam.auth.service

import com.media.bus.common.exceptions.BaseException
import com.media.bus.common.exceptions.BusinessException
import com.media.bus.common.exceptions.NoAuthenticationException
import com.media.bus.common.result.type.CommonResult
import com.media.bus.contract.security.JwtProvider
import com.media.bus.contract.security.MemberPrincipal
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

/**
 * ## 회원 인증/인가 비즈니스 로직 서비스
 *
 * 핵심 비즈니스 규칙:
 * 1. 입력 검증은 `RegisterRequestValidator`(Guard 계층)에 위임한다.
 * 2. 이메일 인증 전 로그인은 허용하나, `emailVerified = false` 상태의 토큰을 발급하여
 *    Gateway / 각 서비스에서 이메일 미인증 사용자를 주요 기능에서 차단한다.
 * 3. 이메일 인증 토큰은 UUID로 생성, Redis에 TTL 24h로 저장한다.
 *    보안상 이유로 토큰은 HTTP 응답에 포함하지 않는다 (이메일 발송 전용).
 */
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

    /**
     * 회원가입 처리.
     * 1. Guard 계층(RegisterRequestValidator)에 입력 검증 위임
     * 2. BCrypt 비밀번호 암호화 저장
     * 3. 역할 조회 및 member_role 저장 (같은 트랜잭션)
     * 4. 이메일 인증 토큰 발급 및 Redis 저장 (응답에는 포함하지 않음)
     */
    @Transactional
    fun register(request: RegisterRequest) {
        // 1. 입력 검증 (Guard 계층에 위임)
        registerRequestValidator.validate(request)

        // 2. Member 생성 (트랜잭션 내에서 auto-persist)
        val member = MemberEntity.create(
            loginId = request.loginId,
            encodedPassword = passwordEncoder.encode(request.password)!!,
            email = request.email,
            phoneNumber = request.phoneNumber,
            businessNumber = request.businessNumber,
            memberName = request.memberName,
        )

        // 3. role 마스터 테이블에서 MemberType에 해당하는 Role 조회 및 역할 부여
        val role = roleRepository.findByName(request.memberType.name)
            ?: throw BaseException(AuthResult.ROLE_NOT_FOUND)
        MemberRoleEntity.of(member, role)

        // 4. 이메일 인증 토큰 생성 및 Redis 저장
        //    보안상 응답에 포함하지 않음 — 실제 서비스에서는 이메일로 발송
        val verifyToken = UUID.randomUUID().toString()
        redisTemplate.opsForValue().set(
            EMAIL_VERIFY_KEY_PREFIX + verifyToken,
            member.id.value.toString(),
            EMAIL_VERIFY_TTL,
        )

        log.info("[AuthService.register] 회원가입 완료. memberId={}, email={}", member.id.value, member.email)
        // TODO: AWS SES 또는 SMTP를 통해 인증 링크를 이메일로 발송해야 한다.
        log.debug("[개발용] 이메일 인증 토큰: {}", verifyToken)
    }

    /**
     * 로그인 처리.
     * 1. 자격증명(아이디/비밀번호) 검증
     * 2. 계정 상태(ACTIVE) 검증
     * 3. 역할 조회 (N+1 방지)
     * 4. Access Token + Refresh Token 발급 (신규 세션 ID 생성)
     *
     * `emailVerified = false` 상태에서도 로그인은 허용한다.
     *
     * @param deviceInfo User-Agent 등 기기 정보 — 다중 디바이스 세션 목록 노출용 (선택)
     * @param ip         클라이언트 IP — 세션 관리 UI용 (선택)
     */
    @Transactional(readOnly = true)
    fun login(request: LoginRequest, deviceInfo: String? = null, ip: String? = null): AuthTokenResult {
        val member = memberRepository.findByLoginId(request.loginId)
            ?: throw NoAuthenticationException(CommonResult.BAD_CREDENTIAL_FAIL)

        if (!passwordEncoder.matches(request.password, member.password)) {
            throw NoAuthenticationException(CommonResult.BAD_CREDENTIAL_FAIL)
        }

        // 비활성/정지/탈퇴 계정 로그인 차단
        // INACTIVE는 사용자 본인이 비활성화한 상태로, `/auth/reactivate` 엔드포인트를 통해 복귀 가능하다.
        when (member.status) {
            MemberStatus.INACTIVE -> throw NoAuthenticationException(CommonResult.ACCOUNT_INACTIVE_FAIL)
            MemberStatus.SUSPENDED -> throw NoAuthenticationException(CommonResult.ACCOUNT_SUSPENDED_FAIL)
            MemberStatus.WITHDRAWN -> throw NoAuthenticationException(CommonResult.ACCOUNT_WITHDRAWN_FAIL)
            MemberStatus.ACTIVE -> { /* 정상 처리 */ }
        }

        val memberType = roleResolutionService.resolveMemberType(member.id.value)

        val principal = MemberPrincipal(
            id = member.id.value,
            loginId = member.loginId,
            email = member.email,
            memberType = memberType,
            emailVerified = member.emailVerified,
            permissions = emptySet(),
        )

        // DB에서 역할에 매핑된 권한 목록을 조회하여 JWT claim에 포함
        val permissionNames = rolePermissionRepository.findPermissionNamesByRoleName(memberType.name)

        val accessToken = jwtProvider.generateAccessToken(principal, permissionNames)
        // 신규 로그인은 항상 새 세션으로 취급 — 다중 디바이스 병행 로그인을 허용한다.
        val sessionId = UUID.randomUUID().toString()
        val refreshToken = jwtProvider.generateRefreshToken(
            memberId = member.id.value.toString(),
            sessionId = sessionId,
            deviceInfo = deviceInfo,
            ip = ip,
        )

        log.info("[AuthService.login] 로그인 성공. memberId={}, sessionId={}", member.id.value, sessionId)
        auditLogService.success(
            actorId = member.id.value,
            actorType = resolveActorType(memberType),
            action = AuditAction.LOGIN,
            targetType = AuditTargetType.MEMBER,
            targetId = member.id.value.toString(),
            detail = """{"sessionId":"$sessionId"}""",
        )
        return AuthTokenResult(accessToken, refreshToken)
    }

    /**
     * 이메일 인증 처리.
     * Redis에서 인증 토큰으로 회원 ID를 조회하고, emailVerified 플래그를 업데이트한다.
     */
    @Transactional
    fun verifyEmail(token: String) {
        val memberId = redisTemplate.opsForValue().get(EMAIL_VERIFY_KEY_PREFIX + token)
            ?: throw NoAuthenticationException(CommonResult.EMAIL_TOKEN_INVALID_FAIL)

        val member = memberRepository.findById(UUID.fromString(memberId))
            ?: throw NoAuthenticationException(CommonResult.USER_NOT_FOUND_FAIL)

        member.verifyEmail()
        // 인증 완료 후 Redis 토큰 즉시 삭제 (1회성 토큰)
        redisTemplate.delete(EMAIL_VERIFY_KEY_PREFIX + token)

        log.info("[AuthService.verifyEmail] 이메일 인증 완료. memberId={}", memberId)
    }

    /**
     * Refresh Token을 검증하고 새로운 Access Token을 발급한다.
     * Redis에 저장된 토큰과 요청 토큰을 비교하여 탈취 여부를 검증한다.
     */
    @Transactional(readOnly = true)
    fun refreshAccessToken(refreshToken: String): AuthTokenResult {
        // 서명/만료 검증 + 파싱을 tryParseClaims() 단일 호출로 통합
        val claims = jwtProvider.tryParseClaims(refreshToken)
            ?: throw NoAuthenticationException(CommonResult.ACCESS_TOKEN_EXPIRED_FAIL)
        val memberId = claims.subject
        // sid claim은 신규 세션 구조에서만 존재한다. 없는 경우(하위 호환 토큰) 전체 조회로 fallback 한다.
        val sessionId = claims[JwtProvider.SESSION_ID_CLAIM] as? String

        // Redis에 저장된 토큰과 비교 (Refresh Token Rotation 지원)
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

        // 최신 역할 정보 반영
        val memberType = roleResolutionService.resolveMemberType(member.id.value)

        val principal = MemberPrincipal(
            id = member.id.value,
            loginId = member.loginId,
            email = member.email,
            memberType = memberType,
            emailVerified = member.emailVerified,
            permissions = emptySet(),
        )

        // 토큰 재발급 시에도 DB에서 최신 권한을 조회하여 갱신된 권한이 즉시 반영되도록 함
        val permissionNames = rolePermissionRepository.findPermissionNamesByRoleName(memberType.name)

        val newAccessToken = jwtProvider.generateAccessToken(principal, permissionNames)
        // Token Rotation: 동일 sessionId를 유지하여 기기 추적을 보존한다.
        // sid가 없는 레거시 토큰에서는 새 sid를 발급한다 (점진 이관).
        val newRefreshToken = jwtProvider.generateRefreshToken(
            memberId = memberId,
            sessionId = sessionId ?: UUID.randomUUID().toString(),
            deviceInfo = null,
            ip = null,
        )

        return AuthTokenResult(newAccessToken, newRefreshToken)
    }

    /**
     * 2차 본인 인증.
     * 현재 로그인한 사용자의 비밀번호를 재확인하여 본인 여부를 검증한다.
     * 회원정보 수정, 비밀번호 변경, 탈퇴 등 민감한 작업 전에 호출한다.
     *
     * 인증 성공 시 Redis에 `member-verify:{memberId}` 키를 5분 TTL로 저장하여
     * 후속 민감 API에서 인증 여부를 확인할 수 있도록 한다.
     */
    @Transactional(readOnly = true)
    fun verifyMember(memberId: String, request: VerifyMemberRequest) {
        val member = memberRepository.findById(UUID.fromString(memberId))
            ?: throw NoAuthenticationException(CommonResult.USER_NOT_FOUND_FAIL)

        if (!passwordEncoder.matches(request.password, member.password)) {
            throw NoAuthenticationException(CommonResult.AUTHENTICATION_FAIL)
        }

        // 2차 인증 성공 상태를 Redis에 저장 (5분 TTL)
        redisTemplate.opsForValue().set(
            MEMBER_VERIFY_KEY_PREFIX + memberId,
            "verified",
            MEMBER_VERIFY_TTL,
        )

        log.info("[AuthService.verifyMember] 2차 본인 인증 성공. memberId={}", memberId)
    }

    /**
     * 2차 본인 인증 완료 여부를 확인한다.
     * Redis에 인증 상태가 존재하지 않으면 예외를 발생시킨다.
     */
    fun checkVerified(memberId: String) {
        val verified = redisTemplate.opsForValue().get(MEMBER_VERIFY_KEY_PREFIX + memberId)
        if (verified == null) {
            throw NoAuthenticationException(AuthResult.VERIFY_REQUIRED)
        }
    }

    /**
     * 2차 본인 인증 상태를 Redis에서 삭제한다.
     * 민감한 작업 완료 후 호출하여 1회성으로 사용할 수 있다.
     */
    fun clearVerification(memberId: String) {
        redisTemplate.delete(MEMBER_VERIFY_KEY_PREFIX + memberId)
    }

    /** MemberType 이름 접두사로 감사 로그 행위자 유형을 구분한다 (ADMIN_* → ADMIN, 그 외 → MEMBER). */
    private fun resolveActorType(memberType: com.media.bus.contract.entity.member.MemberType): AuditActorType =
        if (memberType.name.startsWith("ADMIN")) AuditActorType.ADMIN else AuditActorType.MEMBER

    /**
     * 로그아웃 처리. 특정 세션만 삭제하여 다른 디바이스는 유지한다.
     * sessionId가 null이면(레거시 호출) 모든 세션을 삭제한다.
     */
    fun logout(memberId: String, sessionId: String? = null) {
        if (sessionId != null) {
            jwtProvider.deleteSession(memberId, sessionId)
            log.info("[AuthService.logout] 세션 로그아웃 처리. memberId={}, sessionId={}", memberId, sessionId)
        } else {
            jwtProvider.deleteRefreshToken(memberId)
            log.info("[AuthService.logout] 전체 세션 로그아웃. memberId={}", memberId)
        }
        auditLogService.success(
            actorId = runCatching { UUID.fromString(memberId) }.getOrNull(),
            actorType = AuditActorType.MEMBER,
            action = AuditAction.LOGOUT,
            targetType = AuditTargetType.SESSION,
            targetId = sessionId,
        )
    }

    // ─────────────────────────────────────────────────────────────────
    // 비밀번호 초기화
    // ─────────────────────────────────────────────────────────────────

    /**
     * 비밀번호 초기화 요청.
     * loginId 또는 email로 회원을 조회하여 Redis에 1시간 TTL의 초기화 토큰을 저장한다.
     * 회원이 존재하지 않아도 동일한 응답을 반환하여 이메일 열거 공격을 방지한다.
     */
    fun requestPasswordReset(request: PasswordResetRequest) {
        passwordResetValidator.validate(request)

        // loginId 또는 email로 회원 조회
        val member = request.loginId?.let { memberRepository.findByLoginId(it) }
            ?: request.email?.let { memberRepository.findByEmail(it) }

        // 회원이 없거나 ACTIVE가 아니면 조용히 반환 (이메일 열거 공격 방지)
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
        // TODO: AWS SES 또는 SMTP를 통해 초기화 링크를 이메일로 발송해야 한다.
        log.debug("[개발용] 비밀번호 초기화 토큰: {}", resetToken)
    }

    /**
     * 비밀번호 초기화 토큰 유효성 확인.
     * 프론트엔드에서 토큰 유효 여부를 사전 확인할 때 사용한다. 토큰을 소비하지 않는다.
     */
    fun verifyPasswordResetToken(token: String) {
        redisTemplate.opsForValue().get(PASSWORD_RESET_KEY_PREFIX + token)
            ?: throw BusinessException(AuthResult.PASSWORD_RESET_TOKEN_INVALID)
    }

    /**
     * 로그인 상태에서 사용자가 직접 비밀번호를 변경한다.
     *
     * 비밀번호 초기화(`confirmPasswordReset`)와 달리 **현재 비밀번호**로 본인 검증을 수행하므로
     * 별도의 2차 본인 인증(`/auth/verify`)이 필요하지 않다.
     *
     * 처리 절차:
     * 1. 현재 비밀번호 일치 여부 확인 — 불일치 시 `CURRENT_PASSWORD_MISMATCH`
     * 2. 새 비밀번호가 현재 비밀번호와 동일한지 확인 — 동일 시 거부
     * 3. 새 비밀번호로 변경 (BCrypt 인코딩)
     * 4. 기존 Refresh Token 삭제 — 다른 디바이스 강제 로그아웃 (탈취 방어)
     *
     * **사이드 이펙트**: 모든 Refresh Token이 무효화되므로 현재 세션을 포함한 모든 디바이스가
     * 다음 토큰 재발급 시점에 로그아웃된다. 세션 관리 API 도입 이후에는 현재 세션만 유지하도록 개선 예정.
     */
    @Transactional
    fun changePassword(memberId: String, request: PasswordChangeRequest) {
        val member = memberRepository.findById(UUID.fromString(memberId))
            ?: throw BaseException(AuthResult.MEMBER_NOT_FOUND)

        // 1. 현재 비밀번호 검증
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

        // 2. 동일 비밀번호 재사용 방지 (간단한 정책 — 직전 비밀번호만 체크)
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

        // 3. 비밀번호 변경
        member.changePassword(passwordEncoder.encode(request.newPassword)!!)

        // 4. 기존 Refresh Token 무효화 — 비밀번호 변경 후 재로그인 유도
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

    /**
     * 비밀번호 초기화 확정.
     * 토큰으로 회원을 조회하고 비밀번호를 변경한 뒤, 토큰 삭제 + 기존 세션을 무효화한다.
     */
    @Transactional
    fun confirmPasswordReset(request: PasswordResetConfirmRequest) {
        val memberId = redisTemplate.opsForValue().get(PASSWORD_RESET_KEY_PREFIX + request.token)
            ?: throw BusinessException(AuthResult.PASSWORD_RESET_TOKEN_INVALID)

        val member = memberRepository.findById(UUID.fromString(memberId))
            ?: throw BaseException(AuthResult.MEMBER_NOT_FOUND)

        member.changePassword(passwordEncoder.encode(request.newPassword)!!)

        // 1회성 토큰 삭제
        redisTemplate.delete(PASSWORD_RESET_KEY_PREFIX + request.token)
        // 기존 Refresh Token 무효화 — 비밀번호 변경 후 재로그인 유도
        jwtProvider.deleteRefreshToken(memberId)

        log.info("[AuthService.confirmPasswordReset] 비밀번호 초기화 완료. memberId={}", memberId)
        auditLogService.success(
            actorId = member.id.value,
            actorType = AuditActorType.ANONYMOUS, // 토큰 기반 복구라 로그인 전 상태
            action = AuditAction.PASSWORD_RESET_CONFIRM,
            targetType = AuditTargetType.MEMBER,
            targetId = memberId,
        )
    }

    // ─────────────────────────────────────────────────────────────────
    // 계정 재활성화
    // ─────────────────────────────────────────────────────────────────

    /**
     * 사용자가 스스로 비활성화(INACTIVE)한 계정을 다시 활성화한다.
     *
     * 로그인 플로우와 유사하게 `loginId + password`로 본인을 검증한 뒤 상태를 ACTIVE로 전환하고
     * 즉시 Access/Refresh Token을 발급하여 UX를 단절시키지 않는다.
     *
     * **보안 고려사항**
     * - SUSPENDED/WITHDRAWN 계정은 이 API로 복귀할 수 없다 (관리자 개입 또는 재가입 필요).
     * - 비밀번호가 틀린 경우 `BAD_CREDENTIAL_FAIL`을 던져 계정 존재 여부를 노출하지 않는다.
     */
    @Transactional
    fun reactivate(request: LoginRequest, deviceInfo: String? = null, ip: String? = null): AuthTokenResult {
        val member = memberRepository.findByLoginId(request.loginId)
            ?: throw NoAuthenticationException(CommonResult.BAD_CREDENTIAL_FAIL)

        if (!passwordEncoder.matches(request.password, member.password)) {
            throw NoAuthenticationException(CommonResult.BAD_CREDENTIAL_FAIL)
        }

        if (member.status != MemberStatus.INACTIVE) {
            throw BusinessException(CommonResult.ACCOUNT_NOT_INACTIVE_FAIL)
        }

        val previousStatus = member.status
        member.reactivate()

        // 상태 변경 이력 저장 — 본인 재활성화이므로 changedBy = 본인
        MemberStatusHistoryEntity.create(
            member = member,
            previousStatus = previousStatus,
            newStatus = MemberStatus.ACTIVE,
            reason = "사용자 본인 재활성화",
            changedBy = member,
        )

        // 토큰 발급 — 로그인과 동일 절차
        val memberType = roleResolutionService.resolveMemberType(member.id.value)
        val principal = MemberPrincipal(
            id = member.id.value,
            loginId = member.loginId,
            email = member.email,
            memberType = memberType,
            emailVerified = member.emailVerified,
            permissions = emptySet(),
        )
        val permissionNames = rolePermissionRepository.findPermissionNamesByRoleName(memberType.name)
        val accessToken = jwtProvider.generateAccessToken(principal, permissionNames)
        val sessionId = UUID.randomUUID().toString()
        val refreshToken = jwtProvider.generateRefreshToken(
            memberId = member.id.value.toString(),
            sessionId = sessionId,
            deviceInfo = deviceInfo,
            ip = ip,
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

    // ─────────────────────────────────────────────────────────────────
    // 세션 관리 — 다중 디바이스 로그인 지원
    // ─────────────────────────────────────────────────────────────────

    /**
     * 특정 회원의 활성 세션 목록을 반환한다.
     * Refresh Token 원문은 외부에 노출되지 않으며 `SessionResponse`에서 제외된다.
     *
     * @param currentSessionId 현재 요청자의 세션 ID — 일치 세션에 `current=true` 플래그를 붙인다
     */
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

    /**
     * 현재 세션을 제외한 모든 세션을 로그아웃 시킨다.
     * "다른 기기에서 로그아웃" UX 구현용.
     */
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
