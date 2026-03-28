package com.media.bus.iam.admin.guard;

import com.media.bus.common.exceptions.BaseException;
import com.media.bus.common.exceptions.NoAuthenticationException;
import com.media.bus.common.result.type.CommonResult;
import com.media.bus.iam.admin.dto.CreateAdminMemberRequest;
import com.media.bus.iam.auth.result.AuthResult;
import com.media.bus.iam.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/// 어드민 멤버 생성 요청 전용 검증기.
/// 기존 RegisterRequestValidator와 반대 방향의 allowlist:
/// ADMIN 계열 타입만 허용하고 일반 회원 타입은 거부합니다.
/// 검증 순서:
/// 1. ADMIN 타입 허용 목록 검사 (MemberType.isAdmin() false → 거부)
/// 2. loginId 중복 검사
/// 3. email 중복 검사
@Component
@RequiredArgsConstructor
public class AdminRegisterRequestValidator {

    private final MemberRepository memberRepository;

    public void validate(CreateAdminMemberRequest request) {
        // 1. ADMIN 계열 타입 외 거부
        //    MEMBER, BUSINESS 타입으로 어드민 계정을 생성하는 시도를 차단합니다.
        if (!request.memberType().isAdmin()) {
            throw new BaseException(AuthResult.ADMIN_TYPE_REQUIRED);
        }

        // 2. loginId 중복 검사
        if (memberRepository.existsByLoginId(request.loginId())) {
            throw new NoAuthenticationException(CommonResult.DUPLICATE_USERNAME_FAIL);
        }

        // 3. email 중복 검사
        if (memberRepository.existsByEmail(request.email())) {
            throw new NoAuthenticationException(CommonResult.DUPLICATE_EMAIL_FAIL);
        }
    }
}
