package com.media.bus.common.exceptions

import com.media.bus.common.result.Result
import com.media.bus.common.result.type.CommonResult

/**
 * ## Root Exception
 *
 * Kotlin 기본 인수를 활용하여 단일 주생성자로 모든 조합을 지원한다.
 * result와 message는 이 클래스에서만 선언하며, 하위 클래스는 중복 선언하지 않는다.
 */
open class BaseException(
    val result: Result = CommonResult.ERROR,
    message: String? = null,
    cause: Throwable? = null,
) : RuntimeException(
    message ?: result.message,
    cause,
) {
    final override val message: String =
        message ?: result.message
}
