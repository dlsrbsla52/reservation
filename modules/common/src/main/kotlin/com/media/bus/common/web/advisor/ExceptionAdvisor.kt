package com.media.bus.common.web.advisor

import com.media.bus.common.exceptions.*
import com.media.bus.common.result.Result
import com.media.bus.common.result.type.CommonResult
import com.media.bus.common.web.response.ErrorView
import io.github.resilience4j.bulkhead.BulkheadFullException
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import java.time.Instant

/**
 * ## 중앙 집중 예외 처리기
 *
 * 모든 예외는 buildErrorResponse 팩토리 메서드를 통해 일관된 ErrorView로 변환된다.
 */
@ControllerAdvice
class ExceptionAdvisor {

    private val log = LoggerFactory.getLogger(ExceptionAdvisor::class.java)

    /**
     * Bulkhead 동시 요청 한도 초과 시 429 Too Many Requests로 응답.
     *
     * DB 커넥션 풀 고갈 방지를 위한 `TransactionalBulkheadAspect`가 퍼밋을 거부할 때 발생한다.
     * 클라이언트는 잠시 대기 후 재시도해야 하며, `Retry-After: 1` 헤더로 최소 대기 시간을 안내한다.
     */
    @ExceptionHandler(BulkheadFullException::class)
    fun bulkheadFullHandler(request: HttpServletRequest, error: BulkheadFullException): ResponseEntity<ErrorView> {
        log.warn("bulkheadFullHandler: Bulkhead 동시 요청 한도 초과. uri={}", request.requestURI)
        val headers = HttpHeaders()
        headers.set(HttpHeaders.RETRY_AFTER, "1")
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .headers(headers)
            .contentType(MediaType.APPLICATION_JSON)
            .body(buildErrorView(HttpStatus.TOO_MANY_REQUESTS, CommonResult.BULKHEAD_FULL, null, request, null))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun validationHandler(request: HttpServletRequest, error: MethodArgumentNotValidException): ResponseEntity<ErrorView> {
        val fieldErrors = error.bindingResult.fieldErrors.map { fe ->
            ErrorView.FieldErrorDetail(fe.field, fe.defaultMessage)
        }
        return buildErrorResponse(HttpStatus.BAD_REQUEST, CommonResult.VALIDATION_FAIL, null, request, fieldErrors)
    }

    @ExceptionHandler(StorageException::class)
    fun storageExceptionHandler(request: HttpServletRequest, error: StorageException): ResponseEntity<ErrorView> {
        log.error("storageExceptionHandler.error : ", error)
        return buildErrorResponse(HttpStatus.NOT_FOUND, error.result, error.message, request)
    }

    @ExceptionHandler(AccessDeniedException::class)
    fun accessDeniedHandler(request: HttpServletRequest, error: AccessDeniedException): ResponseEntity<ErrorView> {
        log.error("accessDeniedHandler.error : ", error)
        return buildErrorResponse(HttpStatus.FORBIDDEN, CommonResult.AUTHORIZATION_FAIL, null, request)
    }

    @ExceptionHandler(NoAuthenticationException::class)
    fun noAuthenticationHandler(request: HttpServletRequest, error: NoAuthenticationException): ResponseEntity<ErrorView> {
        log.error("noAuthenticationHandler.error : ", error)
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, error.result, error.message, request)
    }

    @ExceptionHandler(NoAuthorizationException::class)
    fun noAuthorizationHandler(request: HttpServletRequest, error: NoAuthorizationException): ResponseEntity<ErrorView> {
        log.error("noAuthorizationHandler.error : ", error)
        return buildErrorResponse(HttpStatus.FORBIDDEN, error.result, error.message, request)
    }

    /**
     * 예상된 비즈니스 실패(4xx) 처리.
     * `log.warn`으로 기록하여 모니터링에서 기술적 오류(log.error)와 명확히 구분된다.
     */
    @ExceptionHandler(BusinessException::class)
    fun businessExceptionHandler(request: HttpServletRequest, error: BusinessException): ResponseEntity<ErrorView> {
        log.warn("businessExceptionHandler: result={}, message={}, uri={}", error.result.code, error.message, request.requestURI)
        return buildErrorResponse(error.httpStatus, error.result, error.message, request)
    }

    @ExceptionHandler(BaseException::class)
    fun baseExceptionHandler(request: HttpServletRequest, error: BaseException): ResponseEntity<ErrorView> {
        log.error("baseExceptionHandler.error : ", error)
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, error.result, error.message, request)
    }

    @ExceptionHandler(Exception::class)
    fun defaultHandler(request: HttpServletRequest, error: Exception): ResponseEntity<ErrorView> {
        log.error("defaultHandler.error : ", error)
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, CommonResult.ERROR, null, request)
    }

    private fun buildErrorResponse(
        status: HttpStatus,
        result: Result,
        message: String?,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorView> = buildErrorResponse(status, result, message, request, null)

    private fun buildErrorResponse(
        status: HttpStatus,
        result: Result,
        message: String?,
        request: HttpServletRequest,
        errors: List<ErrorView.FieldErrorDetail>?,
    ): ResponseEntity<ErrorView> =
        ResponseEntity.status(status)
            .contentType(MediaType.APPLICATION_JSON)
            .body(buildErrorView(status, result, message, request, errors))

    private fun buildErrorView(
        status: HttpStatus,
        result: Result,
        message: String?,
        request: HttpServletRequest,
        errors: List<ErrorView.FieldErrorDetail>?,
    ): ErrorView = ErrorView(
        code = result.code,
        message = message ?: result.message,
        status = status.value(),
        error = status.reasonPhrase,
        timestamp = Instant.now(),
        path = request.servletPath,
        errors = errors,
    )
}
