package com.media.bus.common.web.advisor;

import com.media.bus.common.exceptions.BaseException;
import com.media.bus.common.exceptions.NoAuthenticationException;
import com.media.bus.common.exceptions.NoAuthorizationException;
import com.media.bus.common.exceptions.StorageException;
import com.media.bus.common.result.Result;
import com.media.bus.common.result.type.CommonResult;
import com.media.bus.common.web.response.ErrorView;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.Instant;
import java.util.List;

/**
 * 중앙 집중 예외 처리기.
 * 모든 예외는 buildErrorResponse 팩토리 메서드를 통해 일관된 ErrorView로 변환된다.
 */
@Slf4j
@ControllerAdvice
public class ExceptionAdvisor {

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorView> validationHandler(HttpServletRequest request, MethodArgumentNotValidException error) {
		List<ErrorView.FieldErrorDetail> fieldErrors = error.getBindingResult().getFieldErrors().stream()
				.map(fe -> new ErrorView.FieldErrorDetail(fe.getField(), fe.getDefaultMessage()))
				.toList();
		return buildErrorResponse(HttpStatus.BAD_REQUEST, CommonResult.VALIDATION_FAIL, null, request, fieldErrors);
	}

	@ExceptionHandler(StorageException.class)
	public ResponseEntity<ErrorView> storageExceptionHandler(HttpServletRequest request, StorageException error) {
		log.error("storageExceptionHandler.error : ", error);
		return buildErrorResponse(HttpStatus.NOT_FOUND, error.getResult(), error.getMessage(), request);
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ErrorView> accessDeniedHandler(HttpServletRequest request, AccessDeniedException error) {
		log.error("accessDeniedHandler.error : ", error);
		return buildErrorResponse(HttpStatus.FORBIDDEN, CommonResult.AUTHORIZATION_FAIL, null, request);
	}

	@ExceptionHandler(NoAuthenticationException.class)
	public ResponseEntity<ErrorView> noAuthenticationHandler(HttpServletRequest request, NoAuthenticationException error) {
		log.error("noAuthenticationHandler.error : ", error);
		return buildErrorResponse(HttpStatus.UNAUTHORIZED, error.getResult(), error.getMessage(), request);
	}

	@ExceptionHandler(NoAuthorizationException.class)
	public ResponseEntity<ErrorView> noAuthorizationHandler(HttpServletRequest request, NoAuthorizationException error) {
		log.error("noAuthorizationHandler.error : ", error);
		return buildErrorResponse(HttpStatus.FORBIDDEN, error.getResult(), error.getMessage(), request);
	}

	@ExceptionHandler(BaseException.class)
	public ResponseEntity<ErrorView> baseExceptionHandler(HttpServletRequest request, BaseException error) {
		log.error("baseExceptionHandler.error : ", error);
		return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, error.getResult(), error.getMessage(), request);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorView> defaultHandler(HttpServletRequest request, Exception error) {
		log.error("defaultHandler.error : ", error);
		return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, CommonResult.ERROR, null, request);
	}

	private ResponseEntity<ErrorView> buildErrorResponse(
		HttpStatus status,
		Result result,
		String message,
		HttpServletRequest request
	) {
		return buildErrorResponse(status, result, message, request, null);
	}

	private ResponseEntity<ErrorView> buildErrorResponse(
		HttpStatus status,
		Result result,
		String message,
		HttpServletRequest request,
		List<ErrorView.FieldErrorDetail> errors
	) {
		return ResponseEntity.status(status)
				.contentType(MediaType.APPLICATION_JSON)
				.body(ErrorView.builder()
						.result(result)
						.message(message)
						.status(status.value())
						.error(status.getReasonPhrase())
						.timestamp(Instant.now())
						.path(request.getServletPath())
						.errors(errors)
						.build());
	}
}
