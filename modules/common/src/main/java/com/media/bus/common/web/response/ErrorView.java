package com.media.bus.common.web.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/// API 에러 응답 래퍼.
///
/// `ExceptionAdvisor`가 단독으로 사용하는 순수 독립 레코드.
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorView(
		String code,
		String message,
		int status,
		String error,
		Instant timestamp,
		String path,
		List<FieldErrorDetail> errors
) {
	public record FieldErrorDetail(String field, String message) {}
}