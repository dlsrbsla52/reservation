package com.hig.exceptions;

import com.hig.result.Result;
import com.hig.result.type.CommonResult;

import java.io.Serial;

/**
 * 인증에 실패할 경우 throw
 */
@SuppressWarnings("unused")
public class NoAuthenticationException extends BaseException {

	@Serial
    private static final long serialVersionUID = 6940889666952870454L;

	public NoAuthenticationException() {
		super(CommonResult.AUTHENTICATION_FAIL);
	}

	public NoAuthenticationException(String message) {
		super(CommonResult.AUTHENTICATION_FAIL, message);
	}

	public NoAuthenticationException(String message, Throwable cause) {
		super(CommonResult.AUTHENTICATION_FAIL, message, cause);
	}

	public NoAuthenticationException(Throwable cause) {
		super(CommonResult.AUTHENTICATION_FAIL, cause);
	}

	public NoAuthenticationException(Result result) {
		super(result);
	}

	public NoAuthenticationException(Result result, String message) {
		super(result, message);
	}

	public NoAuthenticationException(Result result, Throwable cause) {
		super(result, cause);
	}

	protected NoAuthenticationException(Result result, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
		super(result, cause, enableSuppression, writableStackTrace);
	}
}
