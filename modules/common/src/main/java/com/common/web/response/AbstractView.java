package com.common.web.response;

import com.common.result.Result;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * 모든 Response View의 공통 추상 클래스.
 * result/code/message 필드와 중복 getter 로직을 한 곳에서 관리한다.
 */
@ToString
@SuperBuilder
public abstract class AbstractView {

	@NonNull
	@Getter
	private final Result result;

	private final String code;

	private final String message;

	public String getCode() {
		return (code == null || code.isEmpty()) ? result.getCode() : code;
	}

	public String getMessage() {
		return (message == null || message.isEmpty()) ? result.getMessage() : message;
	}
}
