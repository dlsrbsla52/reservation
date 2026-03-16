package com.media.bus.common.exceptions;

import com.media.bus.common.result.Result;
import com.media.bus.common.result.type.CommonResult;

import java.io.Serial;

/**
 * Storage 관련 로직을 처리하는 중 예외가 발생할 경우 throw.
 * AWS NoSuchKeyException 등 스토리지 라이브러리 예외는 이 예외로 래핑하여 던진다.
 */
@SuppressWarnings("unused")
public class StorageException extends BaseException {

	@Serial
    private static final long serialVersionUID = 8976424805831311732L;

	public StorageException() {
		super(CommonResult.STORAGE_ERROR);
	}

	public StorageException(String message) {
		super(CommonResult.STORAGE_ERROR, message);
	}

	public StorageException(String message, Throwable cause) {
		super(CommonResult.STORAGE_ERROR, message, cause);
	}

	public StorageException(Throwable cause) {
		super(CommonResult.STORAGE_ERROR, cause);
	}

	public StorageException(Result result) {
		super(result);
	}

	public StorageException(Result result, String message) {
		super(result, message);
	}

	public StorageException(Result result, Throwable cause) {
		super(result, cause);
	}

	protected StorageException(Result result, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
		super(result, cause, enableSuppression, writableStackTrace);
	}
}
