package com.hig.mvc.response;

import com.hig.result.Result;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

/**
 * 일반적인 Data를 담은 Rest View
 */
@ToString
@Builder
public class DataView<T> {
	
	@NonNull
	private Result result;
	
	private String code;
	
	private String message;
	
	@Getter
	private T data;
		
	public String getCode() {
		return (code == null || code.isEmpty())? result.getCode() : code;
	}
	
	public String getMessage() {
		return (message == null || message.isEmpty())? result.getMessage() : message;
	}

}
