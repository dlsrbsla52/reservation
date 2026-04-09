package com.media.bus.iam.admin.guard

import com.media.bus.common.exceptions.BaseException
import com.media.bus.common.exceptions.BusinessException
import com.media.bus.common.result.type.CommonResult
import com.media.bus.iam.admin.dto.CreateAdminMemberRequest
import com.media.bus.iam.auth.result.AuthResult
import com.media.bus.iam.member.repository.MemberRepository
import org.springframework.stereotype.Component

/**
 * ## 어드민 멤버 생성 요청 전용 검증기
 *
 * ADMIN 계열 타입만 허용하고 일반 회원 타입은 거부한다.
 *
 * 검증 순서:
 * 1. ADMIN 타입 허용 목록 검사 (`MemberType.isAdmin() == false` → 거부)
 * 2. loginId 중복 검사
 * 3. email 중복 검사
 */
@Component
class AdminRegisterRequestValidator(
    private val memberRepository: MemberRepository,
) {
    fun validate(request: CreateAdminMemberRequest) {
        // 1. ADMIN 계열 타입 외 거부
        if (!request.memberType.isAdmin) {
            throw BaseException(AuthResult.ADMIN_TYPE_REQUIRED)
        }

        // 2. loginId 중복 검사 — 비즈니스 검증 실패이므로 BusinessException 사용
        if (memberRepository.existsByLoginId(request.loginId)) {
            throw BusinessException(CommonResult.DUPLICATE_USERNAME_FAIL)
        }

        // 3. email 중복 검사
        if (memberRepository.existsByEmail(request.email)) {
            throw BusinessException(CommonResult.DUPLICATE_EMAIL_FAIL)
        }
    }
}
