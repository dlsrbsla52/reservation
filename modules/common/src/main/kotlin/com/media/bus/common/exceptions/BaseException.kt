package com.media.bus.common.exceptions

import com.media.bus.common.result.Result
import com.media.bus.common.result.type.CommonResult

/**
 * ## Root Exception
 *
 * 메시지는 항상 `Result.message`에서 온다. 예외에 문자열을 직접 넘기는 생성자는 두지 않는다.
 * 하위 클래스는 `result`·`cause`만 전달한다.
 */
open class BaseException(
    val result: Result = CommonResult.ERROR,
    cause: Throwable? = null,
) : RuntimeException(cause) {
    final override val message: String
        get() = result.message
}
