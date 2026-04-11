package com.media.bus.common.result.type

import com.media.bus.common.result.Result
import org.springframework.http.HttpStatus


/**
 * ## 작업 결과에 대한 Enum
 *
 * 00000 ~ 00299 까지는 공통모듈에서 예약한 코드이므로 사용 금지.
 * 99999 는 더미용 코드이므로 사용 자제.
 */
@Suppress("unused")
enum class CommonResult(
    override val code: String,
    override val message: String,
    private val httpStatus: HttpStatus = HttpStatus.BAD_REQUEST,
) : Result {

    // Root
    ROOT("ROOT", "Welcome"),

    // Base
    SUCCESS("00000", "성공하였습니다."),
    ERROR("00100", "오류가 발생하였습니다."),
    FAIL("00200", "실패하였습니다."),

    // Request
    REQUEST_SUCCESS("00010", "요청이 성공하였습니다."),
    REQUEST_ERROR("00110", "요청을 처리하는 중에 오류가 발생하였습니다."),
    REQUEST_FAIL("00210", "요청이 실패하였습니다."),

    // Success
    AUTHENTICATION_SUCCESS("00020", "인증이 성공하였습니다."),

    // Exception or Error
    INTERNAL_ERROR("00120", "내부 오류가 발생하였습니다."),
    SERVICE_ERROR("00130", "서비스 로직을 처리하는 중에 오류가 발생하였습니다."),
    STORAGE_ERROR("00140", "스토리지 작업을 처리하는 중에 오류가 발생하였습니다."),
    STORAGE_NOTFOUND_ERROR("00141", "해당 key의 오브젝트가 존재하지 않습니다."),
    AUTHENTICATION_ERROR("00150", "인증을 진행하는 중 오류가 발생하였습니다."),

    // Fail
    AUTHENTICATION_FAIL("00220", "인증에 실패하였습니다.", HttpStatus.FORBIDDEN),
    BAD_CREDENTIAL_FAIL("00221", "아이디 또는 비밀번호가 잘못되었습니다."),
    USERNAME_NOT_FOUND_FAIL("00222", "유효한 사용자 또는 토큰 정보가 존재하지 않습니다."),
    ACCESS_TOKEN_EXPIRED_FAIL("00223", "토큰 유효기간이 만료되었습니다."),
    ACCESS_TOKEN_VERIFICATION_FAIL("00224", "토큰이 유효하지 않습니다."),
    WHITE_LIST_FAIL("00225", "로그인을 허용하는 IP 또는 클라이언트가 아닙니다."),
    AUTHORIZATION_FAIL("00230", "권한이 없습니다."),
    TOKEN_GENERATE_FAIL("00240", "토큰 생성에 실패하였습니다."),
    TOKEN_VERIFICATION_FAIL("00241", "검증 토큰이 유효하지 않거나 기간이 만료되었습니다."),
    TOKEN_NOEXIST_REVOKE_FAIL("00242", "제거 대상 토큰이 저장소에 존재하지 않습니다."),
    DUPLICATE_USERNAME_FAIL("00250", "이미 사용 중인 아이디입니다.", HttpStatus.CONFLICT),
    DUPLICATE_EMAIL_FAIL("00251", "이미 사용 중인 이메일입니다.", HttpStatus.CONFLICT),
    BUSINESS_NUMBER_REQUIRED_FAIL("00252", "비즈니스 회원 가입 시 사업자 번호는 필수입니다."),
    ACCOUNT_SUSPENDED_FAIL("00253", "이용이 정지된 계정입니다. 고객센터에 문의해주세요.", HttpStatus.FORBIDDEN),
    ACCOUNT_WITHDRAWN_FAIL("00254", "탈퇴된 계정입니다.", HttpStatus.FORBIDDEN),
    EMAIL_TOKEN_INVALID_FAIL("00255", "유효하지 않거나 만료된 이메일 인증 토큰입니다."),
    USER_NOT_FOUND_FAIL("00256", "회원 정보를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    USER_NOT_DENY_ADMIN("00257", "어드민 회원은 신청할 수 없습니다.", HttpStatus.FORBIDDEN),
    VALIDATION_FAIL("00260","입력 값 검증에 실패하였습니다."),
    BULKHEAD_FULL("00270", "서버가 처리할 수 있는 최대 동시 요청 수를 초과하였습니다. 잠시 후 다시 시도해 주세요."),
    ;

    override fun httpStatus(): HttpStatus = httpStatus
}
