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
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * ## AdminRegisterRequestValidator 단위 테스트
 *
 * 검증 순서에 따른 각 분기를 독립적으로 검증한다.
 */
@ExtendWith(MockKExtension::class)
class AdminRegisterRequestValidatorTest {

    @MockK
    private lateinit var memberRepository: MemberRepository

    @InjectMockKs
    private lateinit var validator: AdminRegisterRequestValidator

    // ──────────────────────────────────────────────────────────────
    // 1. ADMIN 타입 허용 목록 검사
    // ──────────────────────────────────────────────────────────────

    @ParameterizedTest(name = "비ADMIN 타입 [{0}] → ADMIN_TYPE_REQUIRED 예외")
    @EnumSource(value = MemberType::class, names = ["MEMBER", "BUSINESS"])
    @DisplayName("ADMIN 계열이 아닌 타입으로 요청 시 ADMIN_TYPE_REQUIRED 예외를 던져야 한다.")
    fun `validate_nonAdminType_throwsAdminTypeRequired`(nonAdminType: MemberType) {
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
    @DisplayName("ADMIN 계열 타입으로 요청 시 타입 검증을 통과해야 한다.")
    fun `validate_adminType_passesTypeCheck`(adminType: MemberType) {
        val request = buildRequest(adminType)
        every { memberRepository.existsByLoginId(request.loginId) } returns false
        every { memberRepository.existsByEmail(request.email) } returns false

        assertThatNoException().isThrownBy { validator.validate(request) }
    }

    // ──────────────────────────────────────────────────────────────
    // 2. loginId 중복 검사
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("이미 존재하는 loginId로 요청 시 DUPLICATE_USERNAME_FAIL 예외를 던져야 한다.")
    fun `validate_duplicateLoginId_throwsDuplicateUsername`() {
        val request = buildRequest(MemberType.ADMIN_USER)
        every { memberRepository.existsByLoginId(request.loginId) } returns true

        assertThatThrownBy { validator.validate(request) }
            .isInstanceOf(BusinessException::class.java)
    }

    // ──────────────────────────────────────────────────────────────
    // 3. email 중복 검사
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("이미 존재하는 email로 요청 시 DUPLICATE_EMAIL_FAIL 예외를 던져야 한다.")
    fun `validate_duplicateEmail_throwsDuplicateEmail`() {
        val request = buildRequest(MemberType.ADMIN_MASTER)
        every { memberRepository.existsByLoginId(request.loginId) } returns false
        every { memberRepository.existsByEmail(request.email) } returns true

        assertThatThrownBy { validator.validate(request) }
            .isInstanceOf(BusinessException::class.java)
    }

    // ──────────────────────────────────────────────────────────────
    // 헬퍼
    // ──────────────────────────────────────────────────────────────

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
