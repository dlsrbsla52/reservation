package com.media.bus.app.adapter

import com.media.bus.common.exceptions.*
import com.media.bus.common.result.type.CommonResult
import io.github.resilience4j.bulkhead.BulkheadFullException
import org.springframework.http.HttpStatus

/** 제공 모듈 호출의 예외 변환 */
internal inline fun <T> callProvider(block: () -> T): T =
    try {
        block()
    } catch (e: BusinessException) {
        throw e
    } catch (e: StorageException) {
        throw e
    } catch (e: NoAuthenticationException) {
        throw e
    } catch (e: NoAuthorizationException) {
        throw e
    } catch (e: BulkheadFullException) {
        throw BusinessException(result = CommonResult.BULKHEAD_FULL, cause = e, httpStatus = HttpStatus.TOO_MANY_REQUESTS)
    } catch (e: BaseException) {
        throw ServiceException(result = CommonResult.ERROR, cause = e)
    }

/** 소비자가 문자열로 넘긴 코드값을 제공자 enum으로 바꾼다. */
internal inline fun <E> parseCode(raw: String, field: String, fromName: (String) -> E?): E =
    fromName(raw) ?: throw BusinessException(CommonResult.VALIDATION_FAIL)

/** [parseCode]의 선택 필터 버전. 비어 있으면 `null`(조건 없음). */
internal inline fun <E> parseOptionalCode(raw: String?, field: String, fromName: (String) -> E?): E? =
    raw?.takeIf { it.isNotBlank() }?.let { parseCode(it, field, fromName) }
