package com.media.bus.common.exceptions

import com.media.bus.common.result.Result
import com.media.bus.common.result.type.CommonResult

/** 해당 기능 또는 자원에 권한이 없을 경우 throw */
class NoAuthorizationException(
    result: Result = CommonResult.AUTHORIZATION_FAIL,
    message: String? = null,
    cause: Throwable? = null,
) : BaseException(result, message, cause) {

    companion object {
        private const val serialVersionUID: Long = 2119428331469523918L
    }
}
