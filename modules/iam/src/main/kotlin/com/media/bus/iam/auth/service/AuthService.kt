package com.media.bus.iam.auth.service

import com.media.bus.common.exceptions.BaseException
import com.media.bus.common.exceptions.NoAuthenticationException
import com.media.bus.common.result.type.CommonResult
import com.media.bus.contract.security.JwtProvider
import com.media.bus.contract.security.MemberPrincipal
import com.media.bus.iam.auth.dto.AuthTokenResult
import com.media.bus.iam.auth.dto.LoginRequest
import com.media.bus.iam.auth.dto.RegisterRequest
import com.media.bus.iam.auth.entity.MemberRoleEntity
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
    private val roleResolutionService: RoleResolutionService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val EMAIL_VERIFY_KEY_PREFIX = "email-verify:"
        private val EMAIL_VERIFY_TTL = Duration.ofHours(24)
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
     * 4. Access Token + Refresh Token 발급
     *
     * `emailVerified = false` 상태에서도 로그인은 허용한다.
     */
    @Transactional(readOnly = true)
    fun login(request: LoginRequest): AuthTokenResult {
        val member = memberRepository.findByLoginId(request.loginId)
            ?: throw NoAuthenticationException(CommonResult.BAD_CREDENTIAL_FAIL)

        if (!passwordEncoder.matches(request.password, member.password)) {
            throw NoAuthenticationException(CommonResult.BAD_CREDENTIAL_FAIL)
        }

        // 이용 정지 / 탈퇴 계정 로그인 차단
        when (member.status) {
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
        val refreshToken = jwtProvider.generateRefreshToken(member.id.value.toString())

        log.info("[AuthService.login] 로그인 성공. memberId={}", member.id.value)
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

        // Redis에 저장된 토큰과 비교 (Refresh Token Rotation 지원)
        if (!jwtProvider.validateRefreshToken(memberId, refreshToken)) {
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
        val newRefreshToken = jwtProvider.generateRefreshToken(memberId) // Token Rotation

        return AuthTokenResult(newAccessToken, newRefreshToken)
    }

    /** 로그아웃 처리. Redis에서 Refresh Token을 삭제하여 서버 측 무효화한다. */
    fun logout(memberId: String) {
        jwtProvider.deleteRefreshToken(memberId)
        log.info("[AuthService.logout] 로그아웃 처리. memberId={}", memberId)
    }

}
