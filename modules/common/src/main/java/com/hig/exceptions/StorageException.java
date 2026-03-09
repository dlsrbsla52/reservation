package com.hig.exceptions;

import com.hig.types.CommonResult;
import com.hig.types.Result;
import com.hig.utils.MessageUtil;
import lombok.Getter;

/**
 * Storage 관련 로직을 처리하는 중 예외가 발생할 경우 throw
 */
public class StorageException extends BaseException {

	private static final long serialVersionUID = 8976424805831311732L;
	
	@Getter
	private final Result result;
	
	@Getter
	private final String message;
	
	public StorageException() {
		super(CommonResult.STORAGE_ERROR);
		this.result = CommonResult.STORAGE_ERROR;
		this.message = CommonResult.STORAGE_ERROR.getMessage();
	}
	
	public StorageException(String message) {
		super(message);
		this.result = CommonResult.STORAGE_ERROR;
		this.message = message;
	}
	
	public StorageException(String message, Throwable cause) {
		super(message, cause);
		this.result = CommonResult.STORAGE_ERROR;
		this.message = message;
	}
	
	public StorageException(Throwable cause) {
		super(cause);
		this.result = CommonResult.STORAGE_ERROR;
		this.message = CommonResult.STORAGE_ERROR.getMessage();
	}
	
	public StorageException(Result result) {
		super(result);
		this.result = result;
		this.message = result.getMessage(MessageUtil::getMessage, result.getMessageId());
	}
	
	public StorageException(Result result, String message) {
		super(result, message);
		this.result = result;
		this.message = message;
	}

	public StorageException(Result result, Throwable cause) {
		super(result, cause);
		this.result = result;
		this.message = result.getMessage(MessageUtil::getMessage, result.getMessageId());
	}
	
    protected StorageException(Result result, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
    	super(result, cause, enableSuppression, writableStackTrace);
    	this.result = result;
    	this.message = result.getMessage(MessageUtil::getMessage, result.getMessageId());
    }

}
