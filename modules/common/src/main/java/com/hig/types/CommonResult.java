package com.hig.types;

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
	BAD_CREDENTIAL_FAIL("00221", "authentication,bad-credential.fail.msg", "username 또는 password 정보가 잘못되었습니다."),
	USERNAME_NOT_FOUND_FAIL("00222", "authentication.username-not-found.fail.msg", "유효한 사용자 또는 토큰 정보가 존재하지 않습니다."),
	ACCESS_TOKEN_EXPIRED_FAIL("00223", "authentication.access-token-expired.fail.msg", "토큰 유효기간이 만료되었습니다."),
	ACCESS_TOKEN_VERIFICATION_FAIL("00224", "authentication.access-token-verification.fail.msg", "토큰이 유효하지 않습니다."),
	WHITE_LIST_FAIL("00225", "authentication.white-list.fail.msg", "로그인을 허용하는 IP 또는 클라이언트가 아닙니다."),
	AUTHORIZATION_FAIL("00230", "authorization.fail.msg", "권한이 없습니다."),
	TOKEN_GENERATE_FAIL("00240", "token.generate.fail.msg", "토큰 생성에 실패하였습니다."),
	TOKEN_VERIFICATION_FAIL("00241", "token.verification.fail.msg", "검증 토큰이 유효하지 않거나 기간이 만료되었습니다."),
	TOKEN_NOEXIST_REVOKE_FAIL("00242", "token.no-exist.revoke.fail.msg", "제거 대상 토큰이 저장소에 존재하지 않습니다.");

	
	@Getter
	private String code;

	@Getter
	private String messageId;
	
	@Getter
	private String message;
	
	
	CommonResult(String code, String messageId, String message) {
		this.code = code;
		this.messageId = messageId;
		this.message = message;
	}

	/**
	 * operator의 실행결과로 받은 메시지가 존재할 경우 그 메시지를 리턴하며 존재하지 않을 경우 message의 값을 리턴함
	 * 
	 * @param operator
	 * @param id
	 * @return message
	 */
	@Override
	public String getMessage(UnaryOperator<String> operator, String id) {
		String bundleMessage = operator.apply(id);
		if (bundleMessage == null || "".equals(bundleMessage)) {
			return getMessage();
		}
		return bundleMessage;
	}

}
