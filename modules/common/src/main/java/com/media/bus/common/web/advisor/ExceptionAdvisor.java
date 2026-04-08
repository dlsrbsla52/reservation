package com.media.bus.common.web.advisor;

import com.media.bus.common.exceptions.BaseException;
import com.media.bus.common.exceptions.BusinessException;
import com.media.bus.common.exceptions.NoAuthenticationException;
import com.media.bus.common.exceptions.NoAuthorizationException;
import com.media.bus.common.exceptions.StorageException;
import com.media.bus.common.result.Result;
import com.media.bus.common.result.type.CommonResult;
import com.media.bus.common.web.response.ErrorView;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.Instant;
import java.util.List;

/// 중앙 집중 예외 처리기.
/// 모든 예외는 buildErrorResponse 팩토리 메서드를 통해 일관된 ErrorView로 변환된다.
@Slf4j
@ControllerAdvice
public class ExceptionAdvisor {

	/// Bulkhead 동시 요청 한도 초과 시 429 Too Many Requests로 응답.
	///
	/// DB 커넥션 풀 고갈 방지를 위한 `TransactionalBulkheadAspect`가 퍼밋을 거부할 때 발생한다.
	/// 클라이언트는 잠시 대기 후 재시도해야 하며, `Retry-After: 1` 헤더로 최소 대기 시간을 안내한다.
	@ExceptionHandler(BulkheadFullException.class)
	public ResponseEntity<ErrorView> bulkheadFullHandler(HttpServletRequest request, BulkheadFullException error) {
		log.warn("bulkheadFullHandler: Bulkhead 동시 요청 한도 초과. uri={}", request.getRequestURI());
		HttpHeaders headers = new HttpHeaders();
		headers.set(HttpHeaders.RETRY_AFTER, "1");
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
				.headers(headers)
				.contentType(MediaType.APPLICATION_JSON)
				.body(buildErrorView(HttpStatus.TOO_MANY_REQUESTS, CommonResult.BULKHEAD_FULL, null, request, null));
	}

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

	/// 예상된 비즈니스 실패(4xx) 처리.
	/// `log.warn`으로 기록하여 모니터링에서 기술적 오류(log.error)와 명확히 구분된다.
	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ErrorView> businessExceptionHandler(HttpServletRequest request, BusinessException error) {
		log.warn("businessExceptionHandler: result={}, message={}, uri={}", error.getResult().getCode(), error.getMessage(), request.getRequestURI());
		return buildErrorResponse(error.getHttpStatus(), error.getResult(), error.getMessage(), request);
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
				.body(buildErrorView(status, result, message, request, errors));
	}

	private ErrorView buildErrorView(
		HttpStatus status,
		Result result,
		String message,
		HttpServletRequest request,
		List<ErrorView.FieldErrorDetail> errors
	) {
		return new ErrorView(
				result.getCode(),
				message != null ? message : result.getMessage(),
				status.value(),
				status.getReasonPhrase(),
				Instant.now(),
				request.getServletPath(),
				errors
		);
	}
}
