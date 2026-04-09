package com.media.bus.common.exceptions

import com.media.bus.common.result.Result
import com.media.bus.common.result.type.CommonResult

/**
 * ## Storage 관련 로직을 처리하는 중 예외가 발생할 경우 throw
 *
 * AWS NoSuchKeyException 등 스토리지 라이브러리 예외는 이 예외로 래핑하여 던진다.
 */
class StorageException(
    result: Result = CommonResult.STORAGE_ERROR,
    message: String? = null,
    cause: Throwable? = null,
) : BaseException(result, message, cause) {

    companion object {
        private const val serialVersionUID: Long = 8976424805831311732L
    }
}
