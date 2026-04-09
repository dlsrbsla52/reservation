package com.media.bus.common.exceptions

import com.media.bus.common.result.Result
import com.media.bus.common.result.type.CommonResult

/**
 * ## Storage 관련 로직을 처리하는 중 예외가 발생할 경우 throw
 *
 * AWS NoSuchKeyException 등 스토리지 라이브러리 예외는 이 예외로 래핑하여 던진다.
 */
@Suppress("unused")
class StorageException : BaseException {

    constructor() : super(CommonResult.STORAGE_ERROR)
    constructor(message: String) : super(CommonResult.STORAGE_ERROR, message)
    constructor(message: String, cause: Throwable) : super(CommonResult.STORAGE_ERROR, message, cause)
    constructor(cause: Throwable) : super(CommonResult.STORAGE_ERROR, cause)
    constructor(result: Result) : super(result)
    constructor(result: Result, message: String) : super(result, message)
    constructor(result: Result, cause: Throwable) : super(result, cause)
    protected constructor(result: Result, cause: Throwable, enableSuppression: Boolean, writableStackTrace: Boolean)
        : super(result, cause, enableSuppression, writableStackTrace)

    companion object {
        private const val serialVersionUID: Long = 8976424805831311732L
    }
}
