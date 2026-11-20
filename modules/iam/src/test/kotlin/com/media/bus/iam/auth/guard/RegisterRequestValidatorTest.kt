package com.media.bus.iam.auth.guard

import com.media.bus.common.exceptions.BusinessException
import com.media.bus.common.result.type.CommonResult
import com.media.bus.contract.entity.member.MemberType
import com.media.bus.iam.auth.dto.RegisterRequest
import com.media.bus.iam.member.repository.MemberRepository
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.util.function.Consumer

@ExtendWith(MockKExtension::class)
@DisplayName("RegisterRequestValidator 단위 테스트")
class RegisterRequestValidatorTest {

    @MockK
    private lateinit var memberRepository: MemberRepository

    @InjectMockKs
    private lateinit var validator: RegisterRequestValidator

    @ParameterizedTest(name = "일반 타입 [{0}] → 타입 검증 통과")
    @EnumSource(value = MemberType::class, names = ["MEMBER", "BUSINESS"])
    fun `허용 타입 통과`(type: MemberType) {
        val request = buildRequest(type, businessNumber = if (type == MemberType.BUSINESS) "123-45-67890" else null)
        every { memberRepository.existsByLoginId(request.loginId) } returns false
        every { memberRepository.existsByEmail(request.email) } returns false

        assertThatNoException().isThrownBy { validator.validate(request) }
    }

    @ParameterizedTest(name = "ADMIN 타입 [{0}] → 거부")
    @EnumSource(value = MemberType::class, names = ["ADMIN_USER", "ADMIN_MASTER", "ADMIN_DEVELOPER"])
    fun `ADMIN 타입 거부`(adminType: MemberType) {
        val request = buildRequest(adminType)

        assertThatThrownBy { validator.validate(request) }
            .isInstanceOf(BusinessException::class.java)
            .satisfies(Consumer { ex ->
                assertThat((ex as BusinessException).result).isEqualTo(CommonResult.USER_NOT_DENY_ADMIN)
            })
    }

    @Test
    fun `BUSINESS 사업자번호 누락`() {
        val request = buildRequest(MemberType.BUSINESS, businessNumber = null)

        assertThatThrownBy { validator.validate(request) }
            .isInstanceOf(BusinessException::class.java)
            .satisfies(Consumer { ex ->
                assertThat((ex as BusinessException).result).isEqualTo(CommonResult.BUSINESS_NUMBER_REQUIRED_FAIL)
            })
    }

    @Test
    fun `loginId 중복`() {
        val request = buildRequest(MemberType.MEMBER)
        every { memberRepository.existsByLoginId(request.loginId) } returns true

        assertThatThrownBy { validator.validate(request) }
            .isInstanceOf(BusinessException::class.java)
            .satisfies(Consumer { ex ->
                assertThat((ex as BusinessException).result).isEqualTo(CommonResult.DUPLICATE_USERNAME_FAIL)
            })
    }

    @Test
    fun `email 중복`() {
        val request = buildRequest(MemberType.MEMBER)
        every { memberRepository.existsByLoginId(request.loginId) } returns false
        every { memberRepository.existsByEmail(request.email) } returns true

        assertThatThrownBy { validator.validate(request) }
            .isInstanceOf(BusinessException::class.java)
            .satisfies(Consumer { ex ->
                assertThat((ex as BusinessException).result).isEqualTo(CommonResult.DUPLICATE_EMAIL_FAIL)
            })
    }

    private fun buildRequest(
        memberType: MemberType,
        businessNumber: String? = null,
    ): RegisterRequest = RegisterRequest(
        memberName = "홍길동",
        loginId = "testuser01",
        password = "Password123!",
        email = "test@example.com",
        phoneNumber = "01012345678",
        memberType = memberType,
        businessNumber = businessNumber,
    )
}
