package com.media.bus.common.web.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

/// API 에러 응답 래퍼.
///
/// `ExceptionAdvisor`가 단독으로 사용하는 순수 독립 클래스.
@Getter
@Builder
public class ErrorView {

	private final String code;
	private final String message;
	private final int status;
	private final String error;
	private final Instant timestamp;
	private final String path;

	@JsonInclude(JsonInclude.Include.NON_NULL)
	private final List<FieldErrorDetail> errors;

	public record FieldErrorDetail(String field, String message) {}
}
