package com.hig.exceptions;

import com.hig.result.type.CommonResult;
import com.hig.result.Result;
import com.hig.utils.MessageUtil;
import lombok.Getter;

/**
 * 인증에 실패할 경우 throw
 */
public class NoAuthenticationException extends BaseException {

	private static final long serialVersionUID = 6940889666952870454L;

	@Getter
	private final Result result;
	
	@Getter
	private final String message;
	
	public NoAuthenticationException() {
		super(CommonResult.AUTHENTICATION_FAIL);
		this.result = CommonResult.AUTHENTICATION_FAIL;
		this.message = CommonResult.AUTHENTICATION_FAIL.getMessage();
	}
	
	public NoAuthenticationException(String message) {
		super(message);
		this.result = CommonResult.AUTHENTICATION_FAIL;
		this.message = message;
	}
	
	public NoAuthenticationException(String message, Throwable cause) {
		super(message, cause);
		this.result = CommonResult.AUTHENTICATION_FAIL;
		this.message = message;
	}
	
	public NoAuthenticationException(Throwable cause) {
		super(cause);
		this.result = CommonResult.AUTHENTICATION_FAIL;
		this.message = CommonResult.AUTHENTICATION_FAIL.getMessage();
	}
	
	public NoAuthenticationException(Result result) {
		super(result);
		this.result = result;
		this.message = result.getMessage(MessageUtil::getMessage, result.getMessageId());
	}
	
	public NoAuthenticationException(Result result, String message) {
		super(result, message);
		this.result = result;
		this.message = message;
	}

	public NoAuthenticationException(Result result, Throwable cause) {
		super(result, cause);
		this.result = result;
		this.message = result.getMessage(MessageUtil::getMessage, result.getMessageId());
	}
	
    protected NoAuthenticationException(Result result, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
    	super(result, cause, enableSuppression, writableStackTrace);
    	this.result = result;
    	this.message = result.getMessage(MessageUtil::getMessage, result.getMessageId());
    }
}
