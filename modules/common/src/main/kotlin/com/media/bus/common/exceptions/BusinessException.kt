package com.media.bus.common.exceptions

import com.media.bus.common.result.Result
import com.media.bus.common.result.type.CommonResult
import org.springframework.http.HttpStatus

/**
 * ## 예상된 비즈니스 실패를 표현하는 예외
 *
 * 입력 검증 실패, 리소스 미존재, 중복 등 정상 비즈니스 흐름의 일부로 발생하는 상황에 사용한다.
 * `ServiceException`(기술적 오류, HTTP 500)과 달리 4xx로 응답되며, 모니터링 알람을 발동시키지 않는다.
 *
 * ## HTTP 상태 결정 방식
 * `Result` 구현체(enum)가 `httpStatus()`를 통해 HTTP 상태를 선언하므로,
 * 호출부에서 `HttpStatus`를 직접 지정할 필요가 없다.
 */
@Suppress("unused")
class BusinessException : BaseException {

    val httpStatus: HttpStatus

    // --- Result 기반 생성자 (권장) ---

    constructor(result: Result) : super(result) {
        this.httpStatus = result.httpStatus()
    }

    constructor(result: Result, message: String) : super(result, message) {
        this.httpStatus = result.httpStatus()
    }

    constructor(result: Result, cause: Throwable) : super(result, cause) {
        this.httpStatus = result.httpStatus()
    }

    // --- Result 없이 메시지만 전달하는 경우 (특정 Result 코드가 없을 때) ---

    /** HTTP 상태를 400 Bad Request로 기본 지정하는 편의 생성자. */
    constructor(message: String) : super(CommonResult.FAIL, message) {
        this.httpStatus = HttpStatus.BAD_REQUEST
    }

    /** Result 없이 HTTP 상태와 메시지만으로 생성하는 편의 생성자. */
    constructor(httpStatus: HttpStatus, message: String) : super(CommonResult.FAIL, message) {
        this.httpStatus = httpStatus
    }

    companion object {
        private const val serialVersionUID: Long = 7381893246823945120L
    }
}
