package com.media.bus.common.exceptions

import com.media.bus.common.result.Result
import com.media.bus.common.result.type.CommonResult
import com.media.bus.common.utils.MessageUtil

/**
 * ## Root Exception
 *
 * result와 message는 이 클래스에서만 선언한다. 하위 클래스는 중복 선언하지 않는다.
 */
open class BaseException : RuntimeException {

    val result: Result
    final override val message: String

    constructor() : super(CommonResult.ERROR.getMessage(MessageUtil::getMessage, CommonResult.ERROR.messageId)) {
        this.result = CommonResult.ERROR
        this.message = CommonResult.ERROR.message
    }

    constructor(message: String) : super(message) {
        this.result = CommonResult.ERROR
        this.message = message
    }

    constructor(message: String, cause: Throwable) : super(message, cause) {
        this.result = CommonResult.ERROR
        this.message = message
    }

    constructor(cause: Throwable) : super(cause) {
        this.result = CommonResult.ERROR
        this.message = CommonResult.ERROR.message
    }

    constructor(result: Result) : super(result.getMessage(MessageUtil::getMessage, result.messageId)) {
        this.result = result
        this.message = result.getMessage(MessageUtil::getMessage, result.messageId)
    }

    constructor(result: Result, message: String) : super(message) {
        this.result = result
        this.message = message
    }

    constructor(result: Result, message: String, cause: Throwable) : super(message, cause) {
        this.result = result
        this.message = message
    }

    constructor(result: Result, cause: Throwable) : super(
        result.getMessage(MessageUtil::getMessage, result.messageId), cause
    ) {
        this.result = result
        this.message = result.getMessage(MessageUtil::getMessage, result.messageId)
    }

    protected constructor(
        result: Result,
        cause: Throwable,
        enableSuppression: Boolean,
        writableStackTrace: Boolean,
    ) : super(result.getMessage(MessageUtil::getMessage, result.messageId), cause, enableSuppression, writableStackTrace) {
        this.result = result
        this.message = result.getMessage(MessageUtil::getMessage, result.messageId)
    }

    companion object {
        private const val serialVersionUID: Long = 3857013638852527498L
    }
}
