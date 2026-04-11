package com.media.bus.iam.auth.result

import com.media.bus.common.result.Result
import org.springframework.http.HttpStatus

/**
 * ## 인증 모듈 전용 결과 코드 Enum
 *
 * 공통 모듈의 `CommonResult`(00000~00299)와 코드가 충돌하지 않도록 A 접두사를 사용한다.
 */
enum class AuthResult(
    override val code: String,
    override val message: String,
    private val httpStatus: HttpStatus = HttpStatus.BAD_REQUEST,
) : Result {

    ROLE_NOT_FOUND("A0001", "역할 정보를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    ADMIN_TYPE_REQUIRED("A0002", "어드민 계정 유형만 생성 가능합니다."),
    VERIFY_REQUIRED("A0003", "2차 본인 인증이 필요합니다."),
    ;

    override fun httpStatus(): HttpStatus = httpStatus
}
