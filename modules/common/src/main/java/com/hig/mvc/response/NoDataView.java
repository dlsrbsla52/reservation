package com.hig.mvc.response;

import com.hig.types.Result;
import lombok.Builder;
import lombok.NonNull;
import lombok.ToString;

/**
 * Data가 없는 Rest View
 */
@ToString
@Builder
public class NoDataView {
	
	@NonNull
	private Result result;
	
	private String code;
	
	private String message;
	
	
	public String getCode() {
		return (code == null || code.isEmpty())? result.getCode() : code;
	}
	
	public String getMessage() {
		return (message == null || message.isEmpty())? result.getMessage() : message;
	}

}
