package com.media.bus.iam.admin.guard;

import com.media.bus.common.exceptions.BaseException;
import com.media.bus.common.exceptions.NoAuthenticationException;
import com.media.bus.contract.entity.member.MemberType;
import com.media.bus.iam.admin.dto.CreateAdminMemberRequest;
import com.media.bus.iam.auth.result.AuthResult;
import com.media.bus.iam.member.repository.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/// AdminRegisterRequestValidator 단위 테스트.
/// 검증 순서에 따른 각 분기를 독립적으로 검증합니다.
@ExtendWith(MockitoExtension.class)
class AdminRegisterRequestValidatorTest {

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private AdminRegisterRequestValidator validator;

    // ──────────────────────────────────────────────────────────────
    // 1. ADMIN 타입 허용 목록 검사
    // ──────────────────────────────────────────────────────────────

    @ParameterizedTest(name = "비ADMIN 타입 [{0}] → ADMIN_TYPE_REQUIRED 예외")
    @EnumSource(value = MemberType.class, names = {"MEMBER", "BUSINESS"})
    @DisplayName("ADMIN 계열이 아닌 타입으로 요청 시 ADMIN_TYPE_REQUIRED 예외를 던져야 한다.")
    void validate_nonAdminType_throwsAdminTypeRequired(MemberType nonAdminType) {
        // given
        CreateAdminMemberRequest request = buildRequest(nonAdminType);

        // when & then
        assertThatThrownBy(() -> validator.validate(request))
            .isInstanceOf(BaseException.class)
            .satisfies(ex -> {
                BaseException base = (BaseException) ex;
                assertThat(base.getResult()).isEqualTo(AuthResult.ADMIN_TYPE_REQUIRED);
            });
    }

    @ParameterizedTest(name = "ADMIN 타입 [{0}] → 타입 검증 통과")
    @EnumSource(value = MemberType.class, names = {"ADMIN_USER", "ADMIN_MASTER", "ADMIN_DEVELOPER"})
    @DisplayName("ADMIN 계열 타입으로 요청 시 타입 검증을 통과해야 한다.")
    void validate_adminType_passesTypeCheck(MemberType adminType) {
        // given — 중복 없음 상태 스텁
        CreateAdminMemberRequest request = buildRequest(adminType);
        when(memberRepository.existsByLoginId(request.loginId())).thenReturn(false);
        when(memberRepository.existsByEmail(request.email())).thenReturn(false);

        // when & then
        assertThatNoException().isThrownBy(() -> validator.validate(request));
    }

    // ──────────────────────────────────────────────────────────────
    // 2. loginId 중복 검사
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("이미 존재하는 loginId로 요청 시 DUPLICATE_USERNAME_FAIL 예외를 던져야 한다.")
    void validate_duplicateLoginId_throwsDuplicateUsername() {
        // given
        CreateAdminMemberRequest request = buildRequest(MemberType.ADMIN_USER);
        when(memberRepository.existsByLoginId(request.loginId())).thenReturn(true);

        // when & then
        assertThatThrownBy(() -> validator.validate(request))
            .isInstanceOf(NoAuthenticationException.class);
    }

    // ──────────────────────────────────────────────────────────────
    // 3. email 중복 검사
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("이미 존재하는 email로 요청 시 DUPLICATE_EMAIL_FAIL 예외를 던져야 한다.")
    void validate_duplicateEmail_throwsDuplicateEmail() {
        // given
        CreateAdminMemberRequest request = buildRequest(MemberType.ADMIN_MASTER);
        when(memberRepository.existsByLoginId(request.loginId())).thenReturn(false);
        when(memberRepository.existsByEmail(request.email())).thenReturn(true);

        // when & then
        assertThatThrownBy(() -> validator.validate(request))
            .isInstanceOf(NoAuthenticationException.class);
    }

    // ──────────────────────────────────────────────────────────────
    // 헬퍼
    // ──────────────────────────────────────────────────────────────

    private CreateAdminMemberRequest buildRequest(MemberType memberType) {
        return CreateAdminMemberRequest.builder()
            .loginId("admin_test01")
            .password("AdminPass123!")
            .email("admin@example.com")
            .phoneNumber("01012345678")
            .memberType(memberType)
            .build();
    }

    // assertThat을 static import 없이 사용하기 위한 위임
    private static <T> org.assertj.core.api.AbstractObjectAssert<?, T> assertThat(T actual) {
        return org.assertj.core.api.Assertions.assertThat(actual);
    }
}
