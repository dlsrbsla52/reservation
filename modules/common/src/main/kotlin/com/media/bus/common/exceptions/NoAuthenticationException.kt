package com.media.bus.common.exceptions

import com.media.bus.common.result.Result
import com.media.bus.common.result.type.CommonResult

/** 인증에 실패할 경우 throw */
class NoAuthenticationException(
    result: Result = CommonResult.AUTHENTICATION_FAIL,
    cause: Throwable? = null,
) : BaseException(result, cause) {

    companion object {
        private const val serialVersionUID: Long = 6940889666952870454L
    }
}
