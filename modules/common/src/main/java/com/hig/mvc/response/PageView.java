package com.hig.mvc.response;

import com.hig.types.Result;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

/**
 * Page 스타일의 Data View
 */
@ToString
@Builder
public class PageView<E> {
	
	@NonNull
	private Result result;

	private String code;

	private String message;
	
	@Getter
	private ListData<E> data;
	
	public String getCode() {
		return (code == null || code.isEmpty())? result.getCode() : code;
	}
	
	public String getMessage() {
		return (message == null || message.isEmpty())? result.getMessage() : message;
	}
}

