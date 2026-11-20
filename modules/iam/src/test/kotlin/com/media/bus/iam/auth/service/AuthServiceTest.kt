package com.media.bus.iam.auth.service

import com.media.bus.common.exceptions.BusinessException
import com.media.bus.common.exceptions.NoAuthenticationException
import com.media.bus.common.exceptions.NoAuthorizationException
import com.media.bus.contract.entity.member.MemberType
import com.media.bus.contract.security.JwtProvider
import com.media.bus.contract.security.TokenAudience
import com.media.bus.iam.auth.dto.*
import com.media.bus.iam.auth.guard.PasswordResetValidator
import com.media.bus.iam.auth.guard.RegisterRequestValidator
import com.media.bus.iam.auth.repository.RolePermissionRepository
import com.media.bus.iam.auth.repository.RoleRepository
import com.media.bus.iam.auth.result.AuthResult
import com.media.bus.iam.member.entity.MemberEntity
import com.media.bus.iam.member.entity.enumerated.MemberStatus
import com.media.bus.iam.member.repository.MemberRepository
import io.jsonwebtoken.Claims
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.assertj.core.api.Assertions.*
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.security.crypto.password.PasswordEncoder
import java.util.*
import java.util.function.Consumer

@ExtendWith(MockKExtension::class)
@DisplayName("AuthService 단위 테스트")
class AuthServiceTest {

    @MockK private lateinit var memberRepository: MemberRepository

    @MockK private lateinit var passwordEncoder: PasswordEncoder

    @MockK private lateinit var jwtProvider: JwtProvider

    @MockK private lateinit var redisTemplate: StringRedisTemplate

    @MockK private lateinit var rolePermissionRepository: RolePermissionRepository

    @MockK private lateinit var roleRepository: RoleRepository

    @MockK private lateinit var registerRequestValidator: RegisterRequestValidator

    @MockK private lateinit var passwordResetValidator: PasswordResetValidator

    @MockK private lateinit var roleResolutionService: RoleResolutionService

    @MockK(relaxed = true) private lateinit var auditLogService: com.media.bus.iam.audit.service.AuditLogService

    @MockK private lateinit var valueOps: ValueOperations<String, String>

    @InjectMockKs private lateinit var authService: AuthService

    private val testMemberId = UUID.randomUUID()
    private val testMemberIdStr = testMemberId.toString()

    private fun mockMember(
        status: MemberStatus = MemberStatus.ACTIVE,
        password: String = "encodedPassword",
    ): MemberEntity {
        val member = mockk<MemberEntity>(relaxed = true)
        val entityId = mockk<EntityID<UUID>>()
        every { entityId.value } returns testMemberId
        every { member.id } returns entityId
        every { member.loginId } returns "testuser"
        every { member.email } returns "test@example.com"
        every { member.password } returns password
        every { member.status } returns status
        every { member.emailVerified } returns true
        return member
    }

    @Nested
    @DisplayName("login")
    inner class Login {

        private val loginRequest = LoginRequest(loginId = "testuser", password = "Password123!")

        @Test
        fun `정상 로그인`() {
            val member = mockMember()
            every { memberRepository.findByLoginId("testuser") } returns member
            every { passwordEncoder.matches("Password123!", "encodedPassword") } returns true
            every { roleResolutionService.resolveMemberType(testMemberId) } returns MemberType.MEMBER
            every { rolePermissionRepository.findPermissionNamesByRoleName("MEMBER") } returns setOf("READ")
            every { jwtProvider.generateAccessToken(any(), any()) } returns "accessToken"
            every {
                jwtProvider.generateRefreshToken(
                    memberId = eq(testMemberIdStr),
                    sessionId = any(),
                    deviceInfo = any(),
                    ip = any(),
                )
            } returns "refreshToken"

            val result = authService.login(loginRequest)

            assertThat(result.accessToken).isEqualTo("accessToken")
            assertThat(result.refreshToken).isEqualTo("refreshToken")
        }

        @Test
        fun `존재하지 않는 아이디`() {
            every { memberRepository.findByLoginId("testuser") } returns null

            assertThatThrownBy { authService.login(loginRequest) }
                .isInstanceOf(NoAuthenticationException::class.java)
        }

        @Test
        fun `비밀번호 불일치`() {
            val member = mockMember()
            every { memberRepository.findByLoginId("testuser") } returns member
            every { passwordEncoder.matches("Password123!", "encodedPassword") } returns false

            assertThatThrownBy { authService.login(loginRequest) }
                .isInstanceOf(NoAuthenticationException::class.java)
        }

        @Test
        fun `정지된 계정`() {
            val member = mockMember(status = MemberStatus.SUSPENDED)
            every { memberRepository.findByLoginId("testuser") } returns member
            every { passwordEncoder.matches("Password123!", "encodedPassword") } returns true

            assertThatThrownBy { authService.login(loginRequest) }
                .isInstanceOf(NoAuthenticationException::class.java)
        }

        @Test
        fun `탈퇴한 계정`() {
            val member = mockMember(status = MemberStatus.WITHDRAWN)
            every { memberRepository.findByLoginId("testuser") } returns member
            every { passwordEncoder.matches("Password123!", "encodedPassword") } returns true

            assertThatThrownBy { authService.login(loginRequest) }
                .isInstanceOf(NoAuthenticationException::class.java)
        }

        private fun givenValidCredential(memberType: MemberType) {
            every { memberRepository.findByLoginId("testuser") } returns mockMember()
            every { passwordEncoder.matches("Password123!", "encodedPassword") } returns true
            every { roleResolutionService.resolveMemberType(testMemberId) } returns memberType
        }

        @Test
        fun `관리자 계정의 일반 사이트 로그인 거부`() {
            givenValidCredential(MemberType.ADMIN_MASTER)

            assertThatThrownBy { authService.login(loginRequest, audience = TokenAudience.USER) }
                .isInstanceOf(NoAuthorizationException::class.java)
                .extracting("result").isEqualTo(AuthResult.ADMIN_SITE_LOGIN_REQUIRED)
            verify(exactly = 0) { jwtProvider.generateAccessToken(any(), any(), any()) }
        }

        @Test
        fun `일반 계정의 관리자 사이트 로그인 거부`() {
            givenValidCredential(MemberType.BUSINESS)

            assertThatThrownBy { authService.login(loginRequest, audience = TokenAudience.ADMIN) }
                .isInstanceOf(NoAuthorizationException::class.java)
                .extracting("result").isEqualTo(AuthResult.ADMIN_ACCOUNT_REQUIRED)
            verify(exactly = 0) { jwtProvider.generateAccessToken(any(), any(), any()) }
        }

        @Test
        fun `관리자 로그인 발급처`() {
            givenValidCredential(MemberType.ADMIN_USER)
            every { rolePermissionRepository.findPermissionNamesByRoleName("ADMIN_USER") } returns setOf("READ")
            every { jwtProvider.generateAccessToken(any(), any(), TokenAudience.ADMIN) } returns "adminAccess"
            every {
                jwtProvider.generateRefreshToken(any(), any(), any(), any(), TokenAudience.ADMIN)
            } returns "adminRefresh"

            val result = authService.login(loginRequest, audience = TokenAudience.ADMIN)

            assertThat(result.accessToken).isEqualTo("adminAccess")
            assertThat(result.refreshToken).isEqualTo("adminRefresh")
        }
    }

    @Nested
    @DisplayName("verifyEmail")
    inner class VerifyEmail {

        @Test
        fun `정상 인증`() {
            val member = mockMember()
            every { redisTemplate.opsForValue() } returns valueOps
            every { valueOps.get("email-verify:valid-token") } returns testMemberIdStr
            every { memberRepository.findById(testMemberId) } returns member
            every { redisTemplate.delete("email-verify:valid-token") } returns true

            assertThatNoException().isThrownBy { authService.verifyEmail("valid-token") }
            verify { member.verifyEmail() }
        }

        @Test
        fun `유효하지 않은 토큰`() {
            every { redisTemplate.opsForValue() } returns valueOps
            every { valueOps.get("email-verify:invalid-token") } returns null

            assertThatThrownBy { authService.verifyEmail("invalid-token") }
                .isInstanceOf(NoAuthenticationException::class.java)
        }
    }

    @Nested
    @DisplayName("refreshAccessToken")
    inner class RefreshAccessToken {

        @Test
        fun `정상 갱신`() {
            val claims = mockk<Claims>()
            every { claims.subject } returns testMemberIdStr
            every { claims[com.media.bus.contract.security.JwtProvider.SESSION_ID_CLAIM] } returns "session-A"
            every { claims.audience } returns setOf("USER")
            every { jwtProvider.tryParseClaims("refreshToken") } returns claims
            every { jwtProvider.validateRefreshToken(testMemberIdStr, "session-A", "refreshToken") } returns true

            val member = mockMember()
            every { memberRepository.findById(testMemberId) } returns member
            every { roleResolutionService.resolveMemberType(testMemberId) } returns MemberType.MEMBER
            every { rolePermissionRepository.findPermissionNamesByRoleName("MEMBER") } returns setOf("READ")
            every { jwtProvider.generateAccessToken(any(), any()) } returns "newAccess"
            every {
                jwtProvider.generateRefreshToken(
                    memberId = eq(testMemberIdStr),
                    sessionId = eq("session-A"),
                    deviceInfo = any(),
                    ip = any(),
                )
            } returns "newRefresh"

            val result = authService.refreshAccessToken("refreshToken", TokenAudience.USER)

            assertThat(result.accessToken).isEqualTo("newAccess")
            assertThat(result.refreshToken).isEqualTo("newRefresh")
        }

        @Test
        fun `파싱 실패`() {
            every { jwtProvider.tryParseClaims("badToken") } returns null

            assertThatThrownBy { authService.refreshAccessToken("badToken", TokenAudience.USER) }
                .isInstanceOf(NoAuthenticationException::class.java)
        }

        @Test
        fun `Redis 불일치`() {
            val claims = mockk<Claims>()
            every { claims.subject } returns testMemberIdStr
            every { claims[com.media.bus.contract.security.JwtProvider.SESSION_ID_CLAIM] } returns "session-X"
            every { claims.audience } returns setOf("USER")
            every { jwtProvider.tryParseClaims("stolenToken") } returns claims
            every { jwtProvider.validateRefreshToken(testMemberIdStr, "session-X", "stolenToken") } returns false

            assertThatThrownBy { authService.refreshAccessToken("stolenToken", TokenAudience.USER) }
                .isInstanceOf(NoAuthenticationException::class.java)
        }

        private fun refreshClaims(audience: Set<String>?): Claims {
            val claims = mockk<Claims>()
            every { claims.subject } returns testMemberIdStr
            every { claims[com.media.bus.contract.security.JwtProvider.SESSION_ID_CLAIM] } returns "session-A"
            every { claims.audience } returns audience
            every { jwtProvider.tryParseClaims("refreshToken") } returns claims
            return claims
        }

        @Test
        fun `발급처 불일치`() {
            refreshClaims(setOf("USER"))

            assertThatThrownBy { authService.refreshAccessToken("refreshToken", TokenAudience.ADMIN) }
                .isInstanceOf(NoAuthenticationException::class.java)
                .extracting("result").isEqualTo(AuthResult.TOKEN_AUDIENCE_MISMATCH)
            verify(exactly = 0) { jwtProvider.validateRefreshToken(any(), any(), any()) }
        }

        @Test
        fun `구버전 토큰은 관리자 갱신 불가`() {
            refreshClaims(null)

            assertThatThrownBy { authService.refreshAccessToken("refreshToken", TokenAudience.ADMIN) }
                .isInstanceOf(NoAuthenticationException::class.java)
                .extracting("result").isEqualTo(AuthResult.TOKEN_AUDIENCE_MISMATCH)
        }

        @Test
        fun `역할 변경 후 갱신 거부`() {
            refreshClaims(setOf("ADMIN"))
            every { jwtProvider.validateRefreshToken(testMemberIdStr, "session-A", "refreshToken") } returns true
            every { memberRepository.findById(testMemberId) } returns mockMember()
            every { roleResolutionService.resolveMemberType(testMemberId) } returns MemberType.MEMBER
            every { jwtProvider.deleteSession(testMemberIdStr, "session-A") } just Runs

            assertThatThrownBy { authService.refreshAccessToken("refreshToken", TokenAudience.ADMIN) }
                .isInstanceOf(NoAuthenticationException::class.java)
                .extracting("result").isEqualTo(AuthResult.TOKEN_AUDIENCE_MISMATCH)
            verify { jwtProvider.deleteSession(testMemberIdStr, "session-A") }
        }

        @Test
        fun `관리자 갱신 발급처 유지`() {
            refreshClaims(setOf("ADMIN"))
            every { jwtProvider.validateRefreshToken(testMemberIdStr, "session-A", "refreshToken") } returns true
            every { memberRepository.findById(testMemberId) } returns mockMember()
            every { roleResolutionService.resolveMemberType(testMemberId) } returns MemberType.ADMIN_MASTER
            every { rolePermissionRepository.findPermissionNamesByRoleName("ADMIN_MASTER") } returns setOf("MANAGE")
            every { jwtProvider.generateAccessToken(any(), any(), TokenAudience.ADMIN) } returns "adminAccess"
            every {
                jwtProvider.generateRefreshToken(testMemberIdStr, "session-A", any(), any(), TokenAudience.ADMIN)
            } returns "adminRefresh"

            val result = authService.refreshAccessToken("refreshToken", TokenAudience.ADMIN)

            assertThat(result.accessToken).isEqualTo("adminAccess")
            assertThat(result.refreshToken).isEqualTo("adminRefresh")
        }
    }

    @Nested
    @DisplayName("2차 본인 인증")
    inner class MemberVerification {

        @Test
        fun `verifyMember 정상`() {
            val member = mockMember()
            every { memberRepository.findById(testMemberId) } returns member
            every { passwordEncoder.matches("Password123!", "encodedPassword") } returns true
            every { redisTemplate.opsForValue() } returns valueOps
            every { valueOps.set(any(), any(), any<java.time.Duration>()) } just Runs

            assertThatNoException().isThrownBy {
                authService.verifyMember(testMemberIdStr, VerifyMemberRequest("Password123!"))
            }
            verify { valueOps.set("member-verify:$testMemberIdStr", "verified", any<java.time.Duration>()) }
        }

        @Test
        fun `verifyMember 비밀번호 불일치`() {
            val member = mockMember()
            every { memberRepository.findById(testMemberId) } returns member
            every { passwordEncoder.matches("WrongPass1!", "encodedPassword") } returns false

            assertThatThrownBy {
                authService.verifyMember(testMemberIdStr, VerifyMemberRequest("WrongPass1!"))
            }.isInstanceOf(NoAuthenticationException::class.java)
        }

        @Test
        fun `checkVerified 성공`() {
            every { redisTemplate.opsForValue() } returns valueOps
            every { valueOps.get("member-verify:$testMemberIdStr") } returns "verified"

            assertThatNoException().isThrownBy { authService.checkVerified(testMemberIdStr) }
        }

        @Test
        fun `checkVerified 미인증`() {
            every { redisTemplate.opsForValue() } returns valueOps
            every { valueOps.get("member-verify:$testMemberIdStr") } returns null

            assertThatThrownBy { authService.checkVerified(testMemberIdStr) }
                .isInstanceOf(NoAuthenticationException::class.java)
                .satisfies(Consumer { ex ->
                    assertThat((ex as NoAuthenticationException).result).isEqualTo(AuthResult.VERIFY_REQUIRED)
                })
        }

        @Test
        fun `clearVerification은 Redis 인증 키를 삭제한다`() {
            every { redisTemplate.delete("member-verify:$testMemberIdStr") } returns true

            authService.clearVerification(testMemberIdStr)

            verify { redisTemplate.delete("member-verify:$testMemberIdStr") }
        }
    }

    @Test
    fun `logout 세션 단위`() {
        every { jwtProvider.deleteSession(testMemberIdStr, "session-A") } just Runs

        authService.logout(testMemberIdStr, "session-A")

        verify { jwtProvider.deleteSession(testMemberIdStr, "session-A") }
        verify(exactly = 0) { jwtProvider.deleteRefreshToken(any()) }
    }

    @Test
    fun `logout 세션 id 없음`() {
        authService.logout(testMemberIdStr, null)

        verify(exactly = 0) { jwtProvider.deleteRefreshToken(any()) }
        verify(exactly = 0) { jwtProvider.deleteSession(any(), any()) }
    }

    @Nested
    @DisplayName("비밀번호 초기화")
    inner class PasswordReset {

        @Test
        fun `requestPasswordReset 정상`() {
            val request = PasswordResetRequest(loginId = "testuser")
            val member = mockMember()
            every { passwordResetValidator.validate(request) } just Runs
            every { memberRepository.findByLoginId("testuser") } returns member
            every { redisTemplate.opsForValue() } returns valueOps
            every { valueOps.set(match { it.startsWith("password-reset:") }, eq(testMemberIdStr), any<java.time.Duration>()) } just Runs

            assertThatNoException().isThrownBy { authService.requestPasswordReset(request) }
            verify { valueOps.set(any(), eq(testMemberIdStr), any<java.time.Duration>()) }
        }

        @Test
        fun `requestPasswordReset 회원 없음`() {
            val request = PasswordResetRequest(email = "unknown@example.com")
            every { passwordResetValidator.validate(request) } just Runs
            every { memberRepository.findByEmail("unknown@example.com") } returns null

            assertThatNoException().isThrownBy { authService.requestPasswordReset(request) }
        }

        @Test
        fun `verifyPasswordResetToken 정상`() {
            every { redisTemplate.opsForValue() } returns valueOps
            every { valueOps.get("password-reset:valid-token") } returns testMemberIdStr

            assertThatNoException().isThrownBy { authService.verifyPasswordResetToken("valid-token") }
        }

        @Test
        fun `verifyPasswordResetToken 만료`() {
            every { redisTemplate.opsForValue() } returns valueOps
            every { valueOps.get("password-reset:expired-token") } returns null

            assertThatThrownBy { authService.verifyPasswordResetToken("expired-token") }
                .isInstanceOf(BusinessException::class.java)
                .satisfies(Consumer { ex ->
                    assertThat((ex as BusinessException).result).isEqualTo(AuthResult.PASSWORD_RESET_TOKEN_INVALID)
                })
        }

        @Test
        fun `confirmPasswordReset 정상`() {
            val request = PasswordResetConfirmRequest(token = "reset-token", newPassword = "NewPass123!")
            val member = mockMember()
            every { redisTemplate.opsForValue() } returns valueOps
            every { valueOps.get("password-reset:reset-token") } returns testMemberIdStr
            every { memberRepository.findById(testMemberId) } returns member
            every { passwordEncoder.encode("NewPass123!") } returns "newEncodedPassword"
            every { redisTemplate.delete("password-reset:reset-token") } returns true
            every { jwtProvider.deleteRefreshToken(testMemberIdStr) } just Runs

            assertThatNoException().isThrownBy { authService.confirmPasswordReset(request) }

            verify { member.changePassword("newEncodedPassword") }
            verify { redisTemplate.delete("password-reset:reset-token") }
            verify { jwtProvider.deleteRefreshToken(testMemberIdStr) }
        }

        @Test
        fun `confirmPasswordReset 유효하지 않은 토큰`() {
            val request = PasswordResetConfirmRequest(token = "bad-token", newPassword = "NewPass123!")
            every { redisTemplate.opsForValue() } returns valueOps
            every { valueOps.get("password-reset:bad-token") } returns null

            assertThatThrownBy { authService.confirmPasswordReset(request) }
                .isInstanceOf(BusinessException::class.java)
                .satisfies(Consumer { ex ->
                    assertThat((ex as BusinessException).result).isEqualTo(AuthResult.PASSWORD_RESET_TOKEN_INVALID)
                })
        }
    }

    @Nested
    @DisplayName("changePassword")
    inner class ChangePassword {

        private val request = PasswordChangeRequest(
            currentPassword = "Current123!",
            newPassword = "NewPass123!",
        )

        @Test
        fun `changePassword 정상`() {
            val member = mockMember(password = "encodedCurrent")
            every { memberRepository.findById(testMemberId) } returns member
            every { passwordEncoder.matches("Current123!", "encodedCurrent") } returns true
            every { passwordEncoder.matches("NewPass123!", "encodedCurrent") } returns false
            every { passwordEncoder.encode("NewPass123!") } returns "encodedNew"
            every { jwtProvider.deleteRefreshToken(testMemberIdStr) } just Runs

            assertThatNoException().isThrownBy { authService.changePassword(testMemberIdStr, request) }

            verify { member.changePassword("encodedNew") }
            verify { jwtProvider.deleteRefreshToken(testMemberIdStr) }
        }

        @Test
        fun `changePassword 현재 비밀번호 불일치`() {
            val member = mockMember(password = "encodedCurrent")
            every { memberRepository.findById(testMemberId) } returns member
            every { passwordEncoder.matches("Current123!", "encodedCurrent") } returns false

            assertThatThrownBy { authService.changePassword(testMemberIdStr, request) }
                .isInstanceOf(NoAuthenticationException::class.java)
                .satisfies(Consumer { ex ->
                    assertThat((ex as NoAuthenticationException).result).isEqualTo(AuthResult.CURRENT_PASSWORD_MISMATCH)
                })

            verify(exactly = 0) { member.changePassword(any()) }
            verify(exactly = 0) { jwtProvider.deleteRefreshToken(any()) }
        }

        @Test
        fun `changePassword 동일 비밀번호`() {
            val member = mockMember(password = "encodedCurrent")
            every { memberRepository.findById(testMemberId) } returns member
            every { passwordEncoder.matches("Current123!", "encodedCurrent") } returns true
            every { passwordEncoder.matches("NewPass123!", "encodedCurrent") } returns true

            assertThatThrownBy { authService.changePassword(testMemberIdStr, request) }
                .isInstanceOf(BusinessException::class.java)
                .satisfies(Consumer { ex ->
                    assertThat((ex as BusinessException).result).isEqualTo(AuthResult.NEW_PASSWORD_SAME_AS_CURRENT)
                })

            verify(exactly = 0) { member.changePassword(any()) }
            verify(exactly = 0) { jwtProvider.deleteRefreshToken(any()) }
        }

        @Test
        fun `changePassword 회원 없음`() {
            every { memberRepository.findById(testMemberId) } returns null

            assertThatThrownBy { authService.changePassword(testMemberIdStr, request) }
                .hasMessageContaining(AuthResult.MEMBER_NOT_FOUND.message)
        }
    }

    @Nested
    @DisplayName("reactivate 사이트 규칙")
    inner class ReactivateAudience {

        private val request = LoginRequest(loginId = "testuser", password = "Password123!")

        private fun givenInactive(memberType: MemberType): MemberEntity {
            val member = mockMember(status = MemberStatus.INACTIVE)
            every { memberRepository.findByLoginId("testuser") } returns member
            every { passwordEncoder.matches("Password123!", "encodedPassword") } returns true
            every { roleResolutionService.resolveMemberType(testMemberId) } returns memberType
            return member
        }

        @Test
        fun `관리자 일반 재활성화 거부`() {
            val member = givenInactive(MemberType.ADMIN_MASTER)

            assertThatThrownBy { authService.reactivate(request, audience = TokenAudience.USER) }
                .isInstanceOf(NoAuthorizationException::class.java)
                .extracting("result").isEqualTo(AuthResult.ADMIN_SITE_LOGIN_REQUIRED)
            verify(exactly = 0) { member.reactivate() }
        }

        @Test
        fun `일반 계정 관리자 재활성화 거부`() {
            val member = givenInactive(MemberType.MEMBER)

            assertThatThrownBy { authService.reactivate(request, audience = TokenAudience.ADMIN) }
                .isInstanceOf(NoAuthorizationException::class.java)
                .extracting("result").isEqualTo(AuthResult.ADMIN_ACCOUNT_REQUIRED)
            verify(exactly = 0) { member.reactivate() }
        }
    }
}
