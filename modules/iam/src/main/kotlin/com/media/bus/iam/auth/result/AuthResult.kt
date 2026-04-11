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
    PERMISSION_NOT_FOUND("A0004", "권한 정보를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    ROLE_PERMISSION_ALREADY_EXISTS("A0005", "해당 역할에 이미 할당된 권한입니다.", HttpStatus.CONFLICT),
    ROLE_PERMISSION_NOT_FOUND("A0006", "역할에 해당 권한이 할당되어 있지 않습니다.", HttpStatus.NOT_FOUND),
    MEMBER_ROLE_NOT_FOUND("A0007", "회원에게 할당된 역할이 없습니다.", HttpStatus.NOT_FOUND),
    PASSWORD_RESET_TOKEN_INVALID("A0008", "유효하지 않거나 만료된 비밀번호 초기화 토큰입니다."),
    PASSWORD_RESET_IDENTIFIER_REQUIRED("A0009", "로그인 아이디 또는 이메일 중 하나를 입력해주세요."),
    MEMBER_NOT_FOUND("A0010", "회원을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    CANNOT_SUSPEND_SELF("A0011", "자기 자신의 계정은 정지할 수 없습니다."),
    CANNOT_SUSPEND_ADMIN_MASTER("A0012", "최고 관리자 계정은 정지할 수 없습니다."),
    MEMBER_NOT_ACTIVE("A0013", "활성 상태가 아닌 회원입니다."),
    MEMBER_NOT_SUSPENDED("A0014", "정지 상태가 아닌 회원은 정지 해제할 수 없습니다."),
    ;

    override fun httpStatus(): HttpStatus = httpStatus
}
