package com.media.bus.common.exceptions;

import com.media.bus.common.result.Result;
import com.media.bus.common.result.type.CommonResult;

import java.io.Serial;

/// 서비스 로직을 처리하는 중 예외가 발생할 경우 throw
@SuppressWarnings("unused")
public class ServiceException extends BaseException {

	@Serial
    private static final long serialVersionUID = -6562069723570246584L;

	public ServiceException() {
		super(CommonResult.SERVICE_ERROR);
	}

	public ServiceException(String message) {
		super(CommonResult.SERVICE_ERROR, message);
	}

	public ServiceException(String message, Throwable cause) {
		super(CommonResult.SERVICE_ERROR, message, cause);
	}

	public ServiceException(Throwable cause) {
		super(CommonResult.SERVICE_ERROR, cause);
	}

	public ServiceException(Result result) {
		super(result);
	}

	public ServiceException(Result result, String message) {
		super(result, message);
	}

	public ServiceException(Result result, Throwable cause) {
		super(result, cause);
	}

	protected ServiceException(Result result, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
		super(result, cause, enableSuppression, writableStackTrace);
	}
}
