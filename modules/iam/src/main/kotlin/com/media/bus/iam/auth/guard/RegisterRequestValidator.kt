package com.media.bus.iam.auth.guard

import com.media.bus.common.exceptions.BusinessException
import com.media.bus.common.result.type.CommonResult
import com.media.bus.contract.entity.member.MemberType
import com.media.bus.iam.auth.dto.RegisterRequest
import com.media.bus.iam.member.repository.MemberRepository
import org.springframework.stereotype.Component

/**
 * ## 회원가입 요청 검증 컴포넌트
 *
 * allowlist 방식으로 등록 가능한 MemberType만 허용하여 관리자 계정의 자가 가입을 차단한다.
 *
 * 검증 순서:
 * 1. 등록 불가 타입 차단 (allowlist 외 모두 거부)
 * 2. 비즈니스 회원 사업자번호 필수 검사
 * 3. loginId 중복 검사
 * 4. email 중복 검사
 */
@Component
class RegisterRequestValidator(
    private val memberRepository: MemberRepository,
) {
    companion object {
        /** 자가 가입이 허용된 회원 유형. ADMIN 계열은 별도 관리자 화면을 통해서만 생성 가능하다. */
        private val REGISTRABLE_TYPES = setOf(MemberType.MEMBER, MemberType.BUSINESS)
    }

    fun validate(request: RegisterRequest) {
        // 1. allowlist 외 타입은 자가 가입 차단
        if (!REGISTRABLE_TYPES.contains(request.memberType)) {
            throw BusinessException(CommonResult.USER_NOT_DENY_ADMIN)
        }

        // 2. 비즈니스 회원은 사업자번호 필수
        if (MemberType.BUSINESS == request.memberType && request.businessNumber.isNullOrBlank()) {
            throw BusinessException(CommonResult.BUSINESS_NUMBER_REQUIRED_FAIL)
        }

        // 3. loginId 중복 검사
        if (memberRepository.existsByLoginId(request.loginId)) {
            throw BusinessException(CommonResult.DUPLICATE_USERNAME_FAIL)
        }

        // 4. email 중복 검사
        if (memberRepository.existsByEmail(request.email)) {
            throw BusinessException(CommonResult.DUPLICATE_EMAIL_FAIL)
        }
    }
}
