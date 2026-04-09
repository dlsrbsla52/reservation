package com.media.bus.common.exceptions

import com.media.bus.common.result.Result
import com.media.bus.common.result.type.CommonResult

/** 인증에 실패할 경우 throw */
class NoAuthenticationException : BaseException {

    constructor() : super(CommonResult.AUTHENTICATION_FAIL)
    constructor(message: String) : super(CommonResult.AUTHENTICATION_FAIL, message)
    constructor(message: String, cause: Throwable) : super(CommonResult.AUTHENTICATION_FAIL, message, cause)
    constructor(cause: Throwable) : super(CommonResult.AUTHENTICATION_FAIL, cause)
    constructor(result: Result) : super(result)
    constructor(result: Result, message: String) : super(result, message)
    constructor(result: Result, cause: Throwable) : super(result, cause)
    protected constructor(result: Result, cause: Throwable, enableSuppression: Boolean, writableStackTrace: Boolean)
        : super(result, cause, enableSuppression, writableStackTrace)

    companion object {
        private const val serialVersionUID: Long = 6940889666952870454L
    }
}
