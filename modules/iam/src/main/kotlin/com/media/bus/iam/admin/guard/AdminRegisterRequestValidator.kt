package com.media.bus.iam.admin.guard

import com.media.bus.common.exceptions.BaseException
import com.media.bus.common.exceptions.BusinessException
import com.media.bus.common.result.type.CommonResult
import com.media.bus.iam.admin.dto.CreateAdminMemberRequest
import com.media.bus.iam.auth.result.AuthResult
import com.media.bus.iam.member.repository.MemberRepository
import org.springframework.stereotype.Component

/** 어드민 멤버 생성 요청 전용 검증기 */
@Component
class AdminRegisterRequestValidator(
    private val memberRepository: MemberRepository,
) {
    fun validate(request: CreateAdminMemberRequest) {
        if (!request.memberType.isAdmin) {
            throw BaseException(AuthResult.ADMIN_TYPE_REQUIRED)
        }

        if (memberRepository.existsByLoginId(request.loginId)) {
            throw BusinessException(CommonResult.DUPLICATE_USERNAME_FAIL)
        }

        if (memberRepository.existsByEmail(request.email)) {
            throw BusinessException(CommonResult.DUPLICATE_EMAIL_FAIL)
        }
    }
}
