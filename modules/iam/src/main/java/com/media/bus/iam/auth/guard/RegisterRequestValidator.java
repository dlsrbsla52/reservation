package com.media.bus.iam.auth.guard;

import com.media.bus.common.exceptions.NoAuthenticationException;
import com.media.bus.common.result.type.CommonResult;
import com.media.bus.contract.entity.member.MemberType;
import com.media.bus.iam.auth.dto.RegisterRequest;
import com.media.bus.iam.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 회원가입 요청 검증 구현체.
 * allowlist 방식으로 등록 가능한 MemberType만 허용하여 관리자 계정의 자가 가입을 차단합니다.
 *
 * 검증 순서:
 * 1. 등록 불가 타입 차단 (allowlist 외 모두 거부)
 * 2. 비즈니스 회원 사업자번호 필수 검사
 * 3. loginId 중복 검사
 * 4. email 중복 검사
 */
@Component
@RequiredArgsConstructor
public class RegisterRequestValidator {

    /**
     * 자가 가입이 허용된 회원 유형.
     * ADMIN 계열은 별도 관리자 화면을 통해서만 생성 가능합니다.
     */
    private static final Set<MemberType> REGISTRABLE_TYPES =
        Set.of(MemberType.MEMBER, MemberType.BUSINESS);

    private final MemberRepository memberRepository;

    public void validate(RegisterRequest request) {
        // 1. allowlist 외 타입은 자가 가입 차단
        if (!REGISTRABLE_TYPES.contains(request.memberType())) {
            throw new NoAuthenticationException(CommonResult.USER_NOT_DENY_ADMIN);
        }

        // 2. 비즈니스 회원은 사업자번호 필수
        if (MemberType.BUSINESS.equals(request.memberType())
                && (request.businessNumber() == null || request.businessNumber().isBlank())) {
            throw new NoAuthenticationException(CommonResult.BUSINESS_NUMBER_REQUIRED_FAIL);
        }

        // 3. loginId 중복 검사
        if (memberRepository.existsByLoginId(request.loginId())) {
            throw new NoAuthenticationException(CommonResult.DUPLICATE_USERNAME_FAIL);
        }

        // 4. email 중복 검사
        if (memberRepository.existsByEmail(request.email())) {
            throw new NoAuthenticationException(CommonResult.DUPLICATE_EMAIL_FAIL);
        }
    }
}
