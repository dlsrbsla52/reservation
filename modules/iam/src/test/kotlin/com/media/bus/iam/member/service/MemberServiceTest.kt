package com.media.bus.iam.member.service

import com.media.bus.common.exceptions.BusinessException
import com.media.bus.common.exceptions.NoAuthenticationException
import com.media.bus.contract.entity.member.MemberType
import com.media.bus.contract.security.JwtProvider
import com.media.bus.iam.auth.result.AuthResult
import com.media.bus.iam.auth.service.AuthService
import com.media.bus.iam.auth.service.RoleResolutionService
import com.media.bus.iam.member.dto.FindLoginIdResponse
import com.media.bus.iam.member.dto.FindMeRequest
import com.media.bus.iam.member.dto.MemberModifyRequest
import com.media.bus.iam.member.entity.MemberEntity
import com.media.bus.iam.member.entity.enumerated.MemberStatus
import com.media.bus.iam.member.repository.MemberRepository
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.OffsetDateTime
import java.util.*

@ExtendWith(MockKExtension::class)
@DisplayName("MemberService 단위 테스트")
class MemberServiceTest {

    @MockK private lateinit var memberRepository: MemberRepository

    @MockK private lateinit var jwtProvider: JwtProvider

    @MockK private lateinit var roleResolutionService: RoleResolutionService

    @MockK private lateinit var authService: AuthService

    @MockK(relaxed = true) private lateinit var auditLogService: com.media.bus.iam.audit.service.AuditLogService

    @InjectMockKs private lateinit var memberService: MemberService

    private val testMemberId = UUID.randomUUID()
    private val testMemberIdStr = testMemberId.toString()

    private fun mockMember(): MemberEntity {
        val member = mockk<MemberEntity>(relaxed = true)
        val entityId = mockk<EntityID<UUID>>()
        every { entityId.value } returns testMemberId
        every { member.id } returns entityId
        every { member.loginId } returns "testuser"
        every { member.email } returns "test@example.com"
        every { member.phoneNumber } returns "01012345678"
        every { member.status } returns MemberStatus.ACTIVE
        every { member.businessNumber } returns null
        every { member.createdAt } returns OffsetDateTime.now()
        every { member.updatedAt } returns OffsetDateTime.now()
        return member
    }

    @Nested
    @DisplayName("회원 조회")
    inner class Find {

        @Test
        fun `findByMemberId 정상`() {
            val member = mockMember()
            every { memberRepository.findById(testMemberId) } returns member
            every { roleResolutionService.resolveMemberType(testMemberId) } returns MemberType.MEMBER

            val result = memberService.findByMemberId(testMemberIdStr)

            assertThat(result.id).isEqualTo(testMemberId)
            assertThat(result.loginId).isEqualTo("testuser")
        }

        @Test
        fun `findByMemberId 없음`() {
            every { memberRepository.findById(testMemberId) } returns null

            assertThatThrownBy { memberService.findByMemberId(testMemberIdStr) }
                .isInstanceOf(BusinessException::class.java)
        }

        @Test
        fun `findByLoginId 정상`() {
            val member = mockMember()
            every { memberRepository.findByLoginId("testuser") } returns member
            every { roleResolutionService.resolveMemberType(testMemberId) } returns MemberType.MEMBER

            val result = memberService.findByLoginId("testuser")

            assertThat(result.loginId).isEqualTo("testuser")
        }

        @Test
        fun `findByLoginId 없음`() {
            every { memberRepository.findByLoginId("unknown") } returns null

            assertThatThrownBy { memberService.findByLoginId("unknown") }
                .isInstanceOf(BusinessException::class.java)
        }

        @Test
        fun `findByEmail 정상`() {
            val member = mockMember()
            every { memberRepository.findByEmail("test@example.com") } returns member
            every { roleResolutionService.resolveMemberType(testMemberId) } returns MemberType.MEMBER

            val result = memberService.findByEmail("test@example.com")

            assertThat(result.email).isEqualTo("test@example.com")
        }

        @Test
        fun `findMe 정상`() {
            val request = FindMeRequest(memberName = "홍길동", phoneNumber = "01012345678", email = null)
            val member = mockMember()
            every { memberRepository.findMe(request) } returns member

            val result = memberService.findMe(request)

            assertThat(result).isEqualTo(FindLoginIdResponse(loginId = "testuser"))
            verify(exactly = 0) { roleResolutionService.resolveMemberType(any()) }
        }
    }

    @Nested
    @DisplayName("민감 작업")
    inner class SensitiveOps {

        @Test
        fun `withdraw 정상`() {
            val member = mockMember()
            every { authService.checkVerified(testMemberIdStr) } just Runs
            every { memberRepository.findById(testMemberId) } returns member
            every { authService.clearVerification(testMemberIdStr) } just Runs

            memberService.withdraw(testMemberIdStr)

            verify { member.withdraw() }
            verify { authService.clearVerification(testMemberIdStr) }
        }

        @Test
        fun `withdraw 2차 인증 미완료`() {
            every { authService.checkVerified(testMemberIdStr) } throws
                NoAuthenticationException(AuthResult.VERIFY_REQUIRED)

            assertThatThrownBy { memberService.withdraw(testMemberIdStr) }
                .isInstanceOf(NoAuthenticationException::class.java)
        }

        @Test
        fun `modify 정상`() {
            val member = mockMember()
            val request = MemberModifyRequest(phoneNumber = "01099998888", email = "new@example.com")
            every { authService.checkVerified(testMemberIdStr) } just Runs
            every { memberRepository.findById(testMemberId) } returns member

            memberService.modify(testMemberIdStr, request)

            verify { member.modify(request) }
        }

        @Test
        fun `modify 2차 인증 미완료`() {
            val request = MemberModifyRequest(phoneNumber = "01099998888", email = "new@example.com")
            every { authService.checkVerified(testMemberIdStr) } throws
                NoAuthenticationException(AuthResult.VERIFY_REQUIRED)

            assertThatThrownBy { memberService.modify(testMemberIdStr, request) }
                .isInstanceOf(NoAuthenticationException::class.java)
        }
    }

    @Test
    fun `아이디 찾기 연락처 누락과 공백은 400 검증 오류이며 저장소를 호출하지 않는다`() {
        listOf(null, "", "  ").forEach { contact ->
            val exception = org.junit.jupiter.api.assertThrows<BusinessException> {
                memberService.findMe(FindMeRequest("홍길동", contact, null))
            }
            assertThat(exception.result).isEqualTo(AuthResult.FIND_ME_KEY_REQUIRED)
            assertThat(exception.httpStatus.value()).isEqualTo(400)
        }
        verify(exactly = 0) { memberRepository.findMe(any()) }
    }
}
