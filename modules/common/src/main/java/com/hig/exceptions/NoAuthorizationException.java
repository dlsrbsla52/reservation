package com.hig.exceptions;

import com.hig.types.CommonResult;
import com.hig.types.Result;
import com.hig.utils.MessageUtil;
import lombok.Getter;

/**
 * 해당 기능 또는 자원에 권한이 없을 경우 throw
 */
public class NoAuthorizationException extends BaseException {

	private static final long serialVersionUID = 2119428331469523918L;

	@Getter
	private final Result result;
	
	@Getter
	private final String message;
	
	public NoAuthorizationException() {
		super(CommonResult.AUTHORIZATION_FAIL);
		this.result = CommonResult.AUTHORIZATION_FAIL;
		this.message = CommonResult.AUTHORIZATION_FAIL.getMessage();
	}
	
	public NoAuthorizationException(String message) {
		super(message);
		this.result = CommonResult.AUTHORIZATION_FAIL;
		this.message = message;
	}
	
	public NoAuthorizationException(String message, Throwable cause) {
		super(message, cause);
		this.result = CommonResult.AUTHORIZATION_FAIL;
		this.message = message;
	}
	
	public NoAuthorizationException(Throwable cause) {
		super(cause);
		this.result = CommonResult.AUTHORIZATION_FAIL;
		this.message = CommonResult.AUTHORIZATION_FAIL.getMessage();
	}
	
	public NoAuthorizationException(Result result) {
		super(result);
		this.result = result;
		this.message = result.getMessage(MessageUtil::getMessage, result.getMessageId());
	}
	
	public NoAuthorizationException(Result result, String message) {
		super(result, message);
		this.result = result;
		this.message = message;
	}

	public NoAuthorizationException(Result result, Throwable cause) {
		super(result, cause);
		this.result = result;
		this.message = result.getMessage(MessageUtil::getMessage, result.getMessageId());
	}
	
    protected NoAuthorizationException(Result result, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
    	super(result, cause, enableSuppression, writableStackTrace);
    	this.result = result;
    	this.message = result.getMessage(MessageUtil::getMessage, result.getMessageId());
    }

}
