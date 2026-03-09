package com.hig.exceptions;

import com.hig.types.CommonResult;
import com.hig.types.Result;
import com.hig.utils.MessageUtil;
import lombok.Getter;

/**
 * Root Exception
 */
public class BaseException extends RuntimeException {

	private static final long serialVersionUID = 3857013638852527498L;
	
	@Getter
	private final Result result;
	
	@Getter
	private final String message;
	
	public BaseException() {
		super(CommonResult.ERROR.getMessage(MessageUtil::getMessage, CommonResult.ERROR.getMessageId()));
		this.result = CommonResult.ERROR;
		this.message = CommonResult.ERROR.getMessage();
	}
	
	public BaseException(String message) {
		super(message);
		this.result = CommonResult.ERROR;
		this.message = message;
	}
	
	public BaseException(String message, Throwable cause) {
		super(message, cause);
		this.result = CommonResult.ERROR;
		this.message = message;
	}
	
	public BaseException(Throwable cause) {
		super(cause);
		this.result = CommonResult.ERROR;
		this.message = CommonResult.ERROR.getMessage();
	}

	public BaseException(Result result) {
		super(result.getMessage(MessageUtil::getMessage, result.getMessageId()));
		this.result = result;
		this.message = result.getMessage(MessageUtil::getMessage, result.getMessageId());
	}
	
	public BaseException(Result result, String message) {
		super(message);
		this.result = result;
		this.message = message;
	}

	public BaseException(Result result, Throwable cause) {
		super(result.getMessage(MessageUtil::getMessage, result.getMessageId()), cause);
		this.result = result;
		this.message = result.getMessage(MessageUtil::getMessage, result.getMessageId());
	}
	
    protected BaseException(Result result, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
    	super(result.getMessage(MessageUtil::getMessage, result.getMessageId()), cause, enableSuppression, writableStackTrace);
		this.result = result;
		this.message = result.getMessage(MessageUtil::getMessage, result.getMessageId());
    }
}
