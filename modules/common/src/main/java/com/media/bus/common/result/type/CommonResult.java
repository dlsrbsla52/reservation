package com.media.bus.common.result.type;

import com.media.bus.common.result.Result;
import lombok.Getter;
import lombok.ToString;

import java.util.function.UnaryOperator;

/**
 * 작업 결과에 대한 Enum
 */
@ToString
public enum CommonResult implements Result {

	
	/*
	* 00000 ~ 00299 까지는 공통모듈에서 예약한 코드이므로 사용 금지
	* 99999 는 더미용 코드이므로 사용 자제
	*/
	
	// Root
	ROOT("ROOT", "", "Welcome"),

	// Base
	SUCCESS("00000", "success.msg", "성공하였습니다."),
	ERROR("00100", "error.msg", "오류가 발생하였습니다."),
	FAIL("00200", "fail.msg", "실패하였습니다."),
	
	// Request
	REQUEST_SUCCESS("00010", "request.success.msg", "요청이 성공하였습니다."),
	REQUEST_ERROR("00110", "request.error.msg", "요청을 처리하는 중에 오류가 발생하였습니다."),
	REQUEST_FAIL("00210", "request.fail.msg", "요청이 실패하였습니다."),
	
	// Success
	AUTHENTICATION_SUCCESS("00020", "authentication.success.msg", "인증이 성공하였습니다."),
	
	// Exception or Error
	INTERNAL_ERROR("00120", "internal.error.msg", "내부 오류가 발생하였습니다."),
	SERVICE_ERROR("00130", "service.error.msg", "서비스 로직을 처리하는 중에 오류가 발생하였습니다."),
	STORAGE_ERROR("00140", "storage.error.msg", "스토리지 작업을 처리하는 중에 오류가 발생하였습니다."),
	STORAGE_NOTFOUND_ERROR("00141", "storage.not-found.error.msg", "해당 key의 오브젝트가 존재하지 않습니다."),
	AUTHENTICATION_ERROR("00150", "authentication.error.msg", "인증을 진행하는 중 오류가 발생하였습니다."),
	
	
	// Fail
	AUTHENTICATION_FAIL("00220", "authentication.fail.msg", "인증에 실패하였습니다."),
	BAD_CREDENTIAL_FAIL("00221", "authentication,bad-credential.fail.msg", "아이디 또는 비밀번호가 잘못되었습니다."),
	USERNAME_NOT_FOUND_FAIL("00222", "authentication.username-not-found.fail.msg", "유효한 사용자 또는 토큰 정보가 존재하지 않습니다."),
	ACCESS_TOKEN_EXPIRED_FAIL("00223", "authentication.access-token-expired.fail.msg", "토큰 유효기간이 만료되었습니다."),
	ACCESS_TOKEN_VERIFICATION_FAIL("00224", "authentication.access-token-verification.fail.msg", "토큰이 유효하지 않습니다."),
	WHITE_LIST_FAIL("00225", "authentication.white-list.fail.msg", "로그인을 허용하는 IP 또는 클라이언트가 아닙니다."),
	AUTHORIZATION_FAIL("00230", "authorization.fail.msg", "권한이 없습니다."),
	TOKEN_GENERATE_FAIL("00240", "token.generate.fail.msg", "토큰 생성에 실패하였습니다."),
	TOKEN_VERIFICATION_FAIL("00241", "token.verification.fail.msg", "검증 토큰이 유효하지 않거나 기간이 만료되었습니다."),
	TOKEN_NOEXIST_REVOKE_FAIL("00242", "token.no-exist.revoke.fail.msg", "제거 대상 토큰이 저장소에 존재하지 않습니다."),
    DUPLICATE_USERNAME_FAIL("00250", "authentication.duplicate-username.fail.msg", "이미 사용 중인 아이디입니다."), // 중복 아이디 체크 실패
    DUPLICATE_EMAIL_FAIL("00251", "authentication.duplicate-email.fail.msg", "이미 사용 중인 이메일입니다."), // 중복 이메일 체크 실패
    BUSINESS_NUMBER_REQUIRED_FAIL("00252", "registration.business-number.required.fail.msg", "비즈니스 회원 가입 시 사업자 번호는 필수입니다."), // 필수 파라미터 누락
    ACCOUNT_SUSPENDED_FAIL("00253", "account.suspended.fail.msg", "이용이 정지된 계정입니다. 고객센터에 문의해주세요."), // 계정 비활성화 상태
    ACCOUNT_WITHDRAWN_FAIL("00254", "account.withdrawn.fail.msg", "탈퇴된 계정입니다."), // 탈퇴 계정 접근
    EMAIL_TOKEN_INVALID_FAIL("00255", "authentication.email-token.invalid.fail.msg", "유효하지 않거나 만료된 이메일 인증 토큰입니다."), // 이메일 인증 실패
    USER_NOT_FOUND_FAIL("00256", "user.not-found.fail.msg", "회원 정보를 찾을 수 없습니다."), // 조회 결과 부재
    USER_NOT_DENY_ADMIN("00257", "user.not-found.fail.msg", "어드민 회원은 신청할 수 없습니다."), // 조회 결과 부재
    VALIDATION_FAIL("00260", "validation.fail.msg", "입력 값 검증에 실패하였습니다.") // @Valid 검증 실패
    ;

	
	@Getter
	private final String code;

	@Getter
	private final String messageId;
	
	@Getter
	private final String message;
	
	
	CommonResult(String code, String messageId, String message) {
		this.code = code;
		this.messageId = messageId;
		this.message = message;
	}

	/**
	 * operator의 실행결과로 받은 메시지가 존재할 경우 그 메시지를 리턴하며 존재하지 않을 경우 message의 값을 리턴함
	 * @param operator 연산자 주어진 ID에 대한 메시지를 검색하거나 변환하는 데 사용되는 단항 연산자
	 * @param id 메시지의 고유 식별자
	 * @return message
	 */
	@Override
	public String getMessage(UnaryOperator<String> operator, String id) {
		String bundleMessage = operator.apply(id);
		if (bundleMessage == null || bundleMessage.isEmpty()) {
			return getMessage();
		}
		return bundleMessage;
	}

}
