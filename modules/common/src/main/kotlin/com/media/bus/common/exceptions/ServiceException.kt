package com.media.bus.common.exceptions

import com.media.bus.common.result.Result
import com.media.bus.common.result.type.CommonResult

/** 서비스 로직을 처리하는 중 예외가 발생할 경우 throw */
class ServiceException(
    result: Result = CommonResult.SERVICE_ERROR,
    message: String? = null,
    cause: Throwable? = null,
) : BaseException(result, message, cause) {

    companion object {
        private const val serialVersionUID: Long = -6562069723570246584L
    }
}
