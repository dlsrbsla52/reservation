package com.media.bus.iam.admin.guard

import com.media.bus.common.exceptions.BaseException
import com.media.bus.common.exceptions.BusinessException
import com.media.bus.contract.entity.member.MemberType
import com.media.bus.iam.admin.dto.CreateAdminMemberRequest
import com.media.bus.iam.auth.result.AuthResult
import com.media.bus.iam.member.repository.MemberRepository
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/** AdminRegisterRequestValidator 단위 테스트 */
@ExtendWith(MockKExtension::class)
class AdminRegisterRequestValidatorTest {

    @MockK
    private lateinit var memberRepository: MemberRepository

    @InjectMockKs
    private lateinit var validator: AdminRegisterRequestValidator

    @ParameterizedTest(name = "비ADMIN 타입 [{0}] → ADMIN_TYPE_REQUIRED 예외")
    @EnumSource(value = MemberType::class, names = ["MEMBER", "BUSINESS"])
    fun `ADMIN 계열이 아닌 타입이면 ADMIN_TYPE_REQUIRED 예외를 던진다`(nonAdminType: MemberType) {
        val request = buildRequest(nonAdminType)

        assertThatThrownBy { validator.validate(request) }
            .isInstanceOf(BaseException::class.java)
            .satisfies(java.util.function.Consumer { ex ->
                val base = ex as BaseException
                assertThat(base.result).isEqualTo(AuthResult.ADMIN_TYPE_REQUIRED)
            })
    }

    @ParameterizedTest(name = "ADMIN 타입 [{0}] → 타입 검증 통과")
    @EnumSource(value = MemberType::class, names = ["ADMIN_USER", "ADMIN_MASTER", "ADMIN_DEVELOPER"])
    fun `ADMIN 계열 타입이면 타입 검증을 통과한다`(adminType: MemberType) {
        val request = buildRequest(adminType)
        every { memberRepository.existsByLoginId(request.loginId) } returns false
        every { memberRepository.existsByEmail(request.email) } returns false

        assertThatNoException().isThrownBy { validator.validate(request) }
    }

    @Test
    fun `이미 존재하는 loginId면 DUPLICATE_USERNAME_FAIL 예외를 던진다`() {
        val request = buildRequest(MemberType.ADMIN_USER)
        every { memberRepository.existsByLoginId(request.loginId) } returns true

        assertThatThrownBy { validator.validate(request) }
            .isInstanceOf(BusinessException::class.java)
    }

    @Test
    fun `이미 존재하는 email이면 DUPLICATE_EMAIL_FAIL 예외를 던진다`() {
        val request = buildRequest(MemberType.ADMIN_MASTER)
        every { memberRepository.existsByLoginId(request.loginId) } returns false
        every { memberRepository.existsByEmail(request.email) } returns true

        assertThatThrownBy { validator.validate(request) }
            .isInstanceOf(BusinessException::class.java)
    }

    private fun buildRequest(memberType: MemberType): CreateAdminMemberRequest =
        CreateAdminMemberRequest(
            loginId = "admin_test01",
            password = "AdminPass123!",
            email = "admin@example.com",
            phoneNumber = "01012345678",
            memberType = memberType,
            memberName = "테스트관리자",
        )
}
