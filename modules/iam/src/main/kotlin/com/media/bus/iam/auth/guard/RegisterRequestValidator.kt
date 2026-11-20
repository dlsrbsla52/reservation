package com.media.bus.iam.auth.guard

import com.media.bus.common.exceptions.BusinessException
import com.media.bus.common.result.type.CommonResult
import com.media.bus.contract.entity.member.MemberType
import com.media.bus.iam.auth.dto.RegisterRequest
import com.media.bus.iam.member.repository.MemberRepository
import org.springframework.stereotype.Component

/** 회원가입 요청 검증 컴포넌트 */
@Component
class RegisterRequestValidator(
    private val memberRepository: MemberRepository,
) {
    companion object {
        private val REGISTRABLE_TYPES = setOf(MemberType.MEMBER, MemberType.BUSINESS)
    }

    fun validate(request: RegisterRequest) {
        if (!REGISTRABLE_TYPES.contains(request.memberType)) {
            throw BusinessException(CommonResult.USER_NOT_DENY_ADMIN)
        }

        if (MemberType.BUSINESS == request.memberType && request.businessNumber.isNullOrBlank()) {
            throw BusinessException(CommonResult.BUSINESS_NUMBER_REQUIRED_FAIL)
        }

        if (memberRepository.existsByLoginId(request.loginId)) {
            throw BusinessException(CommonResult.DUPLICATE_USERNAME_FAIL)
        }

        if (memberRepository.existsByEmail(request.email)) {
            throw BusinessException(CommonResult.DUPLICATE_EMAIL_FAIL)
        }
    }
}
