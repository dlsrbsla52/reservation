package com.media.bus.iam.auth.guard

import com.media.bus.common.exceptions.BusinessException
import com.media.bus.iam.auth.dto.PasswordResetRequest
import com.media.bus.iam.auth.result.AuthResult
import org.springframework.stereotype.Component

/**
 * ## 비밀번호 초기화 요청 검증 Guard
 *
 * loginId 또는 email 중 하나가 반드시 있어야 한다.
 */
@Component
class PasswordResetValidator {

    /** loginId/email 중 최소 하나가 있는지 검증한다. */
    fun validate(request: PasswordResetRequest) {
        if (request.loginId.isNullOrBlank() && request.email.isNullOrBlank()) {
            throw BusinessException(AuthResult.PASSWORD_RESET_IDENTIFIER_REQUIRED)
        }
    }
}
