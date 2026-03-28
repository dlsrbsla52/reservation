package com.media.bus.common.exceptions;

import com.media.bus.common.result.Result;
import com.media.bus.common.result.type.CommonResult;

import java.io.Serial;

/// 해당 기능 또는 자원에 권한이 없을 경우 throw
@SuppressWarnings("unused")
public class NoAuthorizationException extends BaseException {

	@Serial
    private static final long serialVersionUID = 2119428331469523918L;

	public NoAuthorizationException() {
		super(CommonResult.AUTHORIZATION_FAIL);
	}

	public NoAuthorizationException(String message) {
		super(CommonResult.AUTHORIZATION_FAIL, message);
	}

	public NoAuthorizationException(String message, Throwable cause) {
		super(CommonResult.AUTHORIZATION_FAIL, message, cause);
	}

	public NoAuthorizationException(Throwable cause) {
		super(CommonResult.AUTHORIZATION_FAIL, cause);
	}

	public NoAuthorizationException(Result result) {
		super(result);
	}

	public NoAuthorizationException(Result result, String message) {
		super(result, message);
	}

	public NoAuthorizationException(Result result, Throwable cause) {
		super(result, cause);
	}

	protected NoAuthorizationException(Result result, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
		super(result, cause, enableSuppression, writableStackTrace);
	}
}
