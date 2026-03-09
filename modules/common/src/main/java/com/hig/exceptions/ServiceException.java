package com.hig.exceptions;

import com.hig.types.CommonResult;
import com.hig.types.Result;
import com.hig.utils.MessageUtil;
import lombok.Getter;

/**
 * 서비스 로직을 처리하는 중 예외가 발생할 경우 throw
 *
 * @author realninano
 * @since 2019. 8. 14.
 */
public class ServiceException extends BaseException {

	private static final long serialVersionUID = -6562069723570246584L;

	@Getter
	private final Result result;
	
	@Getter
	private final String message;
	
	public ServiceException() {
		super(CommonResult.SERVICE_ERROR);
		this.result = CommonResult.SERVICE_ERROR;
		this.message = CommonResult.SERVICE_ERROR.getMessage();
	}
	
	public ServiceException(String message) {
		super(message);
		this.result = CommonResult.SERVICE_ERROR;
		this.message = message;
	}
	
	public ServiceException(String message, Throwable cause) {
		super(message, cause);
		this.result = CommonResult.SERVICE_ERROR;
		this.message = message;
	}
	
	public ServiceException(Throwable cause) {
		super(cause);
		this.result = CommonResult.SERVICE_ERROR;
		this.message = CommonResult.SERVICE_ERROR.getMessage();
	}
	
	public ServiceException(Result result) {
		super(result);
		this.result = result;
		this.message = result.getMessage(MessageUtil::getMessage, result.getMessageId());
	}
	
	public ServiceException(Result result, String message) {
		super(result, message);
		this.result = result;
		this.message = message;
	}

	public ServiceException(Result result, Throwable cause) {
		super(result, cause);
		this.result = result;
		this.message = result.getMessage(MessageUtil::getMessage, result.getMessageId());
	}
	
    protected ServiceException(Result result, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
    	super(result, cause, enableSuppression, writableStackTrace);
    	this.result = result;
    	this.message = result.getMessage(MessageUtil::getMessage, result.getMessageId());
    }
}
