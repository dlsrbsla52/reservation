package com.hig.mvc.response;

import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/**
 * Error View
 */
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
}
