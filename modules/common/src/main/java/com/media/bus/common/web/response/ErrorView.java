package com.media.bus.common.web.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.List;

/// Error View
@ToString(callSuper = true)
@SuperBuilder
public class ErrorView extends AbstractView {

	@Getter
	private final int status;

	@Getter
	private final Instant timestamp;

	@NonNull
	@Getter
	private final String error;

	@Getter
	private final String path;

	@Getter
	@JsonInclude(JsonInclude.Include.NON_NULL)
	private final List<FieldErrorDetail> errors;

	public record FieldErrorDetail(String field, String message) {}
}
