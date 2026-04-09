package com.media.bus.common.exceptions

import com.media.bus.common.result.Result
import com.media.bus.common.result.type.CommonResult

/** 해당 기능 또는 자원에 권한이 없을 경우 throw */
@Suppress("unused")
class NoAuthorizationException : BaseException {

    constructor() : super(CommonResult.AUTHORIZATION_FAIL)
    constructor(message: String) : super(CommonResult.AUTHORIZATION_FAIL, message)
    constructor(message: String, cause: Throwable) : super(CommonResult.AUTHORIZATION_FAIL, message, cause)
    constructor(cause: Throwable) : super(CommonResult.AUTHORIZATION_FAIL, cause)
    constructor(result: Result) : super(result)
    constructor(result: Result, message: String) : super(result, message)
    constructor(result: Result, cause: Throwable) : super(result, cause)
    protected constructor(result: Result, cause: Throwable, enableSuppression: Boolean, writableStackTrace: Boolean)
        : super(result, cause, enableSuppression, writableStackTrace)

    companion object {
        private const val serialVersionUID: Long = 2119428331469523918L
    }
}
