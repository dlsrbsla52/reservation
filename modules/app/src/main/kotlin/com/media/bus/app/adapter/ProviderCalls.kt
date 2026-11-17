package com.media.bus.app.adapter

import com.media.bus.common.exceptions.*
import com.media.bus.common.result.type.CommonResult
import io.github.resilience4j.bulkhead.BulkheadFullException
import org.springframework.http.HttpStatus

/**
 * ## 제공 모듈 호출의 예외 변환
 *
 * 모듈 간 호출이 HTTP에서 직접 호출로 바뀌면서 없어진 HTTP 경계의 예외 변환을 어댑터에서 재현한다.
 *
 * | 제공자 예외 | 예전 S2S 경로의 결과 | 여기서 바꾸는 예외 |
 * |---|---|---|
 * | `BusinessException`(4xx) | 같은 상태·코드·메시지로 전달 | 그대로 전파 |
 * | `NoAuthenticationException`(401)·`NoAuthorizationException`(403) | 같은 상태·코드·메시지로 전달 | 그대로 전파 |
 * | `StorageException`(404) | 같은 상태·코드·메시지로 전달 | 그대로 전파 |
 * | `BulkheadFullException` | 429/`00270` | `BusinessException(BULKHEAD_FULL, 429)`. `httpStatus`를 빼면 `CommonResult` 기본값 때문에 400이 된다 |
 * | 그 밖의 `BaseException`(`ServiceException` 등, 500) | 소비자 쪽 catch-all 500/`00100`, 일반 메시지 | `ServiceException(ERROR)`. 원인은 `cause`로 남겨 로그에서 볼 수 있다 |
 * | 그 밖의 예외 | 500/`00100` | 그대로 전파(catch-all이 같은 결과를 낸다) |
 *
 * 변환 기준은 "예전에 제공자가 5xx로 응답하던 예외"다. 예외 타입이 아니라 `ExceptionAdvisor`가 매기는 HTTP 상태로 판단한다.
 * - `StorageException`은 `BusinessException`이 아니지만 `ExceptionAdvisor`에서 404다. 예를 들어 관리자 계약 생성에서
 *   정류소가 없으면 reservation `StopResolutionService`가 던지고, 예전에도 404/`00140`으로 전달됐다.
 * - 500으로 바꾸는 줄은 예를 들어 관리자 정류소 일괄 등록에서 서울 공공 API가 실패할 때의 외부 응답(500/`00100`)을 유지한다.
 * - `BusinessException`까지 일괄 변환하면 검증 400·404·409가 깨지므로 하지 않는다.
 */
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

/**
 * 소비자가 문자열로 넘긴 코드값을 제공자 enum으로 바꾼다. 알 수 없는 값이면 400 `VALIDATION_FAIL`.
 *
 * 예전에는 제공자 컨트롤러의 파라미터 변환이 같은 400/`00260`을 냈다. 이 저장소의 `fromName`은 실패 시 `null`을
 * 반환하므로, `null`을 그대로 넘기면 검색 필터가 "조건 없음"으로 바뀌어 전체 조회가 된다. 그래서 반드시 예외로 바꾼다.
 */
internal inline fun <E> parseCode(raw: String, field: String, fromName: (String) -> E?): E =
    fromName(raw) ?: throw BusinessException(CommonResult.VALIDATION_FAIL, "허용되지 않는 값입니다: $field=$raw")

/** [parseCode]의 선택 필터 버전. 비어 있으면 `null`(조건 없음). */
internal inline fun <E> parseOptionalCode(raw: String?, field: String, fromName: (String) -> E?): E? =
    raw?.takeIf { it.isNotBlank() }?.let { parseCode(it, field, fromName) }
