package com.media.bus.common.exceptions

import com.media.bus.common.result.Result
import com.media.bus.common.result.type.CommonResult

/** 서비스 로직을 처리하는 중 예외가 발생할 경우 throw */
@Suppress("unused")
class ServiceException : BaseException {

    constructor() : super(CommonResult.SERVICE_ERROR)
    constructor(message: String) : super(CommonResult.SERVICE_ERROR, message)
    constructor(message: String, cause: Throwable) : super(CommonResult.SERVICE_ERROR, message, cause)
    constructor(cause: Throwable) : super(CommonResult.SERVICE_ERROR, cause)
    constructor(result: Result) : super(result)
    constructor(result: Result, message: String) : super(result, message)
    constructor(result: Result, cause: Throwable) : super(result, cause)
    protected constructor(result: Result, cause: Throwable, enableSuppression: Boolean, writableStackTrace: Boolean)
        : super(result, cause, enableSuppression, writableStackTrace)

    companion object {
        private const val serialVersionUID: Long = -6562069723570246584L
    }
}
