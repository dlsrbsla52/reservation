package com.media.bus.iam.auth.service

import com.media.bus.common.exceptions.BusinessException
import com.media.bus.common.exceptions.NoAuthenticationException
import com.media.bus.contract.entity.member.MemberType
import com.media.bus.contract.security.JwtProvider
import com.media.bus.iam.auth.dto.LoginRequest
import com.media.bus.iam.auth.dto.PasswordResetConfirmRequest
import com.media.bus.iam.auth.dto.PasswordResetRequest
import com.media.bus.iam.auth.dto.VerifyMemberRequest
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

    // ──────────────────────────────────────────────────────────────
    // login
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("login")
    inner class Login {

        private val loginRequest = LoginRequest(loginId = "testuser", password = "Password123!")

        @Test
        @DisplayName("정상 로그인 시 토큰 결과를 반환한다")
        fun `정상 로그인`() {
            val member = mockMember()
            every { memberRepository.findByLoginId("testuser") } returns member
            every { passwordEncoder.matches("Password123!", "encodedPassword") } returns true
            every { roleResolutionService.resolveMemberType(testMemberId) } returns MemberType.MEMBER
            every { rolePermissionRepository.findPermissionNamesByRoleName("MEMBER") } returns setOf("READ")
            every { jwtProvider.generateAccessToken(any(), any()) } returns "accessToken"
            every { jwtProvider.generateRefreshToken(testMemberIdStr) } returns "refreshToken"

            val result = authService.login(loginRequest)

            assertThat(result.accessToken).isEqualTo("accessToken")
            assertThat(result.refreshToken).isEqualTo("refreshToken")
        }

        @Test
        @DisplayName("존재하지 않는 아이디로 로그인 시 예외를 던진다")
        fun `존재하지 않는 아이디`() {
            every { memberRepository.findByLoginId("testuser") } returns null

            assertThatThrownBy { authService.login(loginRequest) }
                .isInstanceOf(NoAuthenticationException::class.java)
        }

        @Test
        @DisplayName("비밀번호 불일치 시 예외를 던진다")
        fun `비밀번호 불일치`() {
            val member = mockMember()
            every { memberRepository.findByLoginId("testuser") } returns member
            every { passwordEncoder.matches("Password123!", "encodedPassword") } returns false

            assertThatThrownBy { authService.login(loginRequest) }
                .isInstanceOf(NoAuthenticationException::class.java)
        }

        @Test
        @DisplayName("정지된 계정으로 로그인 시 예외를 던진다")
        fun `정지된 계정`() {
            val member = mockMember(status = MemberStatus.SUSPENDED)
            every { memberRepository.findByLoginId("testuser") } returns member
            every { passwordEncoder.matches("Password123!", "encodedPassword") } returns true

            assertThatThrownBy { authService.login(loginRequest) }
                .isInstanceOf(NoAuthenticationException::class.java)
        }

        @Test
        @DisplayName("탈퇴한 계정으로 로그인 시 예외를 던진다")
        fun `탈퇴한 계정`() {
            val member = mockMember(status = MemberStatus.WITHDRAWN)
            every { memberRepository.findByLoginId("testuser") } returns member
            every { passwordEncoder.matches("Password123!", "encodedPassword") } returns true

            assertThatThrownBy { authService.login(loginRequest) }
                .isInstanceOf(NoAuthenticationException::class.java)
        }
    }

    // ──────────────────────────────────────────────────────────────
    // verifyEmail
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("verifyEmail")
    inner class VerifyEmail {

        @Test
        @DisplayName("유효한 토큰으로 이메일 인증 성공")
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
        @DisplayName("유효하지 않은 토큰 시 예외를 던진다")
        fun `유효하지 않은 토큰`() {
            every { redisTemplate.opsForValue() } returns valueOps
            every { valueOps.get("email-verify:invalid-token") } returns null

            assertThatThrownBy { authService.verifyEmail("invalid-token") }
                .isInstanceOf(NoAuthenticationException::class.java)
        }
    }

    // ──────────────────────────────────────────────────────────────
    // refreshAccessToken
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("refreshAccessToken")
    inner class RefreshAccessToken {

        @Test
        @DisplayName("유효한 Refresh Token으로 새 토큰을 발급한다")
        fun `정상 갱신`() {
            val claims = mockk<Claims>()
            every { claims.subject } returns testMemberIdStr
            every { jwtProvider.tryParseClaims("refreshToken") } returns claims
            every { jwtProvider.validateRefreshToken(testMemberIdStr, "refreshToken") } returns true

            val member = mockMember()
            every { memberRepository.findById(testMemberId) } returns member
            every { roleResolutionService.resolveMemberType(testMemberId) } returns MemberType.MEMBER
            every { rolePermissionRepository.findPermissionNamesByRoleName("MEMBER") } returns setOf("READ")
            every { jwtProvider.generateAccessToken(any(), any()) } returns "newAccess"
            every { jwtProvider.generateRefreshToken(testMemberIdStr) } returns "newRefresh"

            val result = authService.refreshAccessToken("refreshToken")

            assertThat(result.accessToken).isEqualTo("newAccess")
            assertThat(result.refreshToken).isEqualTo("newRefresh")
        }

        @Test
        @DisplayName("파싱 실패 시 예외를 던진다")
        fun `파싱 실패`() {
            every { jwtProvider.tryParseClaims("badToken") } returns null

            assertThatThrownBy { authService.refreshAccessToken("badToken") }
                .isInstanceOf(NoAuthenticationException::class.java)
        }

        @Test
        @DisplayName("Redis 토큰 불일치 시 예외를 던진다")
        fun `Redis 불일치`() {
            val claims = mockk<Claims>()
            every { claims.subject } returns testMemberIdStr
            every { jwtProvider.tryParseClaims("stolenToken") } returns claims
            every { jwtProvider.validateRefreshToken(testMemberIdStr, "stolenToken") } returns false

            assertThatThrownBy { authService.refreshAccessToken("stolenToken") }
                .isInstanceOf(NoAuthenticationException::class.java)
        }
    }

    // ──────────────────────────────────────────────────────────────
    // verifyMember / checkVerified / clearVerification
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("2차 본인 인증")
    inner class MemberVerification {

        @Test
        @DisplayName("정상 인증 시 Redis에 인증 상태를 저장한다")
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
        @DisplayName("비밀번호 불일치 시 예외를 던진다")
        fun `verifyMember 비밀번호 불일치`() {
            val member = mockMember()
            every { memberRepository.findById(testMemberId) } returns member
            every { passwordEncoder.matches("WrongPass1!", "encodedPassword") } returns false

            assertThatThrownBy {
                authService.verifyMember(testMemberIdStr, VerifyMemberRequest("WrongPass1!"))
            }.isInstanceOf(NoAuthenticationException::class.java)
        }

        @Test
        @DisplayName("checkVerified — 인증 완료 상태면 예외 없음")
        fun `checkVerified 성공`() {
            every { redisTemplate.opsForValue() } returns valueOps
            every { valueOps.get("member-verify:$testMemberIdStr") } returns "verified"

            assertThatNoException().isThrownBy { authService.checkVerified(testMemberIdStr) }
        }

        @Test
        @DisplayName("checkVerified — 미인증 시 VERIFY_REQUIRED 예외")
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
        @DisplayName("clearVerification — Redis 삭제 호출 확인")
        fun `clearVerification`() {
            every { redisTemplate.delete("member-verify:$testMemberIdStr") } returns true

            authService.clearVerification(testMemberIdStr)

            verify { redisTemplate.delete("member-verify:$testMemberIdStr") }
        }
    }

    // ──────────────────────────────────────────────────────────────
    // logout
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("로그아웃 시 Refresh Token을 삭제한다")
    fun `logout`() {
        every { jwtProvider.deleteRefreshToken(testMemberIdStr) } just Runs

        authService.logout(testMemberIdStr)

        verify { jwtProvider.deleteRefreshToken(testMemberIdStr) }
    }

    // ──────────────────────────────────────────────────────────────
    // 비밀번호 초기화
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("비밀번호 초기화")
    inner class PasswordReset {

        @Test
        @DisplayName("정상 요청 시 Redis에 초기화 토큰을 저장한다")
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
        @DisplayName("회원이 없어도 예외 없이 반환한다 (이메일 열거 방지)")
        fun `requestPasswordReset 회원 없음`() {
            val request = PasswordResetRequest(email = "unknown@example.com")
            every { passwordResetValidator.validate(request) } just Runs
            every { memberRepository.findByEmail("unknown@example.com") } returns null

            assertThatNoException().isThrownBy { authService.requestPasswordReset(request) }
        }

        @Test
        @DisplayName("유효한 토큰 검증 시 예외 없음")
        fun `verifyPasswordResetToken 정상`() {
            every { redisTemplate.opsForValue() } returns valueOps
            every { valueOps.get("password-reset:valid-token") } returns testMemberIdStr

            assertThatNoException().isThrownBy { authService.verifyPasswordResetToken("valid-token") }
        }

        @Test
        @DisplayName("만료된 토큰 검증 시 예외를 던진다")
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
        @DisplayName("비밀번호 초기화 확정 시 비밀번호 변경 + 토큰 삭제 + 세션 무효화")
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
        @DisplayName("유효하지 않은 토큰으로 확정 시 예외를 던진다")
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
}
