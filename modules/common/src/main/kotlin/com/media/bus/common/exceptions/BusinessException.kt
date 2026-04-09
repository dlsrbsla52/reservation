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
class BusinessException(
    result: Result = CommonResult.FAIL,
    message: String? = null,
    cause: Throwable? = null,
    val httpStatus: HttpStatus = result.httpStatus(),
) : BaseException(result, message, cause) {

    /** HTTP 상태를 직접 지정하는 편의 생성자. */
    constructor(httpStatus: HttpStatus, message: String) : this(
        result = CommonResult.FAIL,
        message = message,
        httpStatus = httpStatus,
    )

    companion object {
        private const val serialVersionUID: Long = 7381893246823945120L
    }
}
