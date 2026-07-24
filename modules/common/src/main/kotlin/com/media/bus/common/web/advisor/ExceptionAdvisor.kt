package com.media.bus.common.web.advisor

import com.fasterxml.jackson.databind.exc.InvalidFormatException
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
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
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
        log.error("bulkheadFullHandler: Bulkhead 동시 요청 한도 초과. uri={}", request.requestURI)
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
        log.warn("validationHandler: {} {} -> {}", request.method, request.requestURI, fieldErrors)
        return buildErrorResponse(HttpStatus.BAD_REQUEST, CommonResult.VALIDATION_FAIL, null, request, fieldErrors)
    }

    /**
     * 요청 본문 역직렬화 실패(400)를 처리한다.
     *
     * 잘못된 enum 값, 숫자/날짜/UUID 형식 오류, 깨진 JSON 등 클라이언트가 보낸 본문을 해석할 수 없을 때 발생한다.
     * 명백한 클라이언트 입력 오류이므로 500이 아닌 400으로 응답하고 `log.warn`으로 기록한다
     * (기술적 오류가 아니므로 모니터링 알람을 발동시키지 않는다).
     * Jackson `InvalidFormatException`이 원인이면 문제 필드와 거부된 값을 함께 안내한다.
     */
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun messageNotReadableHandler(
        request: HttpServletRequest,
        error: HttpMessageNotReadableException,
    ): ResponseEntity<ErrorView> {
        // 원인이 특정 필드의 형식 오류(InvalidFormatException)면 해당 필드/값을 추출해 안내한다.
        val fieldErrors = (error.cause as? InvalidFormatException)?.let { ife ->
            val field = ife.path.joinToString(".") { it.fieldName ?: "?" }
            listOf(ErrorView.FieldErrorDetail(field, "허용되지 않는 값입니다: ${ife.value}"))
        }
        log.warn("messageNotReadableHandler: {} {} -> {}", request.method, request.requestURI, fieldErrors)
        return buildErrorResponse(
            HttpStatus.BAD_REQUEST, CommonResult.VALIDATION_FAIL, "요청 본문을 해석할 수 없습니다.", request, fieldErrors,
        )
    }

    /**
     * 요청 파라미터 타입 변환 실패(400)를 처리한다.
     *
     * `@RequestParam`/`@PathVariable`에 잘못된 enum·숫자·UUID 값이 전달될 때 발생한다.
     * 명백한 클라이언트 입력 오류이므로 400으로 응답하고 `log.warn`으로 기록한다.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun typeMismatchHandler(
        request: HttpServletRequest,
        error: MethodArgumentTypeMismatchException,
    ): ResponseEntity<ErrorView> {
        val fieldErrors = listOf(ErrorView.FieldErrorDetail(error.name, "허용되지 않는 값입니다: ${error.value}"))
        log.warn("typeMismatchHandler: {} {} -> {}", request.method, request.requestURI, fieldErrors)
        return buildErrorResponse(
            HttpStatus.BAD_REQUEST, CommonResult.VALIDATION_FAIL, "요청 파라미터 값이 올바르지 않습니다.", request, fieldErrors,
        )
    }

    @ExceptionHandler(StorageException::class)
    fun storageExceptionHandler(request: HttpServletRequest, error: StorageException): ResponseEntity<ErrorView> {
        log.error("storageExceptionHandler.error: {} {}", request.method, request.requestURI, error)
        return buildErrorResponse(HttpStatus.NOT_FOUND, error.result, error.message, request)
    }

    @ExceptionHandler(AccessDeniedException::class)
    fun accessDeniedHandler(request: HttpServletRequest, error: AccessDeniedException): ResponseEntity<ErrorView> {
        log.error("accessDeniedHandler.error: {} {}", request.method, request.requestURI, error)
        return buildErrorResponse(HttpStatus.FORBIDDEN, CommonResult.AUTHORIZATION_FAIL, null, request)
    }

    @ExceptionHandler(NoAuthenticationException::class)
    fun noAuthenticationHandler(request: HttpServletRequest, error: NoAuthenticationException): ResponseEntity<ErrorView> {
        log.error("noAuthenticationHandler.error: {} {}", request.method, request.requestURI, error)
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, error.result, error.message, request)
    }

    @ExceptionHandler(NoAuthorizationException::class)
    fun noAuthorizationHandler(request: HttpServletRequest, error: NoAuthorizationException): ResponseEntity<ErrorView> {
        log.error("noAuthorizationHandler.error: {} {}", request.method, request.requestURI, error)
        return buildErrorResponse(HttpStatus.FORBIDDEN, error.result, error.message, request)
    }

    /**
     * 예상된 비즈니스 실패(4xx) 처리.
     * `log.warn`으로 기록하여 모니터링에서 기술적 오류(log.error)와 명확히 구분된다.
     */
    @ExceptionHandler(BusinessException::class)
    fun businessExceptionHandler(request: HttpServletRequest, error: BusinessException): ResponseEntity<ErrorView> {
        log.warn(
            "businessExceptionHandler: {} {} result={} message={}",
            request.method, request.requestURI, error.result.code, error.message,
        )
        return buildErrorResponse(error.httpStatus, error.result, error.message, request)
    }

    @ExceptionHandler(BaseException::class)
    fun baseExceptionHandler(request: HttpServletRequest, error: BaseException): ResponseEntity<ErrorView> {
        log.error("baseExceptionHandler.error: {} {}", request.method, request.requestURI, error)
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, error.result, error.message, request)
    }

    @ExceptionHandler(Exception::class)
    fun defaultHandler(request: HttpServletRequest, error: Exception): ResponseEntity<ErrorView> {
        log.error("defaultHandler.error: {} {}", request.method, request.requestURI, error)
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
