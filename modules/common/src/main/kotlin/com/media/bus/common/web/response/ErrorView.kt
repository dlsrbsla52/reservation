package com.media.bus.common.web.response

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.Instant

/**
 * ## API 에러 응답 래퍼
 *
 * `ExceptionAdvisor`가 단독으로 사용하는 순수 독립 데이터 클래스.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ErrorView(
    val code: String,
    val message: String,
    val status: Int,
    val error: String,
    val timestamp: Instant,
    val path: String,
    val errors: List<FieldErrorDetail>?,
) {
    data class FieldErrorDetail(val field: String, val message: String?)
}
