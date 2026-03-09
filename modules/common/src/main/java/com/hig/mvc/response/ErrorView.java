package com.hig.mvc.response;

import com.hig.types.Result;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

import java.util.Date;

/**
 * Error View
 *
 * @author realninano
 * @since 2019. 8. 16.
 */
@ToString
@Builder
public class ErrorView {
	
	@NonNull
	private Result result;
	
	private String message;
	
	private String code;
	
	@Getter
	private int status;
	
	@Getter
	private Date timestamp;
	
	@Getter
	@NonNull
	private String error;
	
	@Getter
	private String path;
	
	public String getCode() {
		return (code == null || code.isEmpty())? result.getCode() : code;
	}
	
	public String getMessage() {
		return (message == null || message.isEmpty())? result.getMessage() : message;
	}

}
