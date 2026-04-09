package com.media.bus.iam.auth.result

import com.media.bus.common.result.Result
import java.util.function.UnaryOperator

/**
 * ## 인증 모듈 전용 결과 코드 Enum
 *
 * 공통 모듈의 `CommonResult`(00000~00299)와 코드가 충돌하지 않도록 A 접두사를 사용한다.
 */
enum class AuthResult(
    override val code: String,
    override val messageId: String,
    override val message: String,
) : Result {

    ROLE_NOT_FOUND("A0001", "auth.role.not-found.fail.msg", "역할 정보를 찾을 수 없습니다."),
    ADMIN_TYPE_REQUIRED("A0002", "auth.admin-type.required.fail.msg", "어드민 계정 유형만 생성 가능합니다.");

    /** 메시지 번들에 등록된 메시지가 있으면 그것을, 없으면 기본 메시지를 반환한다. */
    override fun getMessage(operator: UnaryOperator<String>, id: String): String {
        val bundleMessage = operator.apply(id)
        return if (bundleMessage.isNullOrEmpty()) message else bundleMessage
    }
}
