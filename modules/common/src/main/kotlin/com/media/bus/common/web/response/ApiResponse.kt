package com.media.bus.common.web.response

import com.fasterxml.jackson.annotation.JsonInclude
import com.media.bus.common.result.type.CommonResult

/**
 * ## 통합 API 성공 응답 래퍼
 *
 * 어떤 인프라 클래스도 상속하지 않는 순수 독립 데이터 클래스.
 * 컨트롤러에서 직접 반환하면 Jackson이 그대로 직렬화한다.
 *
 * ## 팩토리 메서드
 * - `success(data)` -- 데이터가 있는 성공 응답
 * - `success()` -- 데이터 없는 성공 응답
 * - `successWithMessage(message)` -- 커스텀 메시지가 필요한 성공 응답
 * - `page(list)` -- 목록 성공 응답
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApiResponse<T>(
    val code: String,
    val message: String,
    val data: T?,
) {
    companion object {

        /** 데이터가 있는 성공 응답 */
        fun <T> success(data: T): ApiResponse<T> =
            ApiResponse(CommonResult.SUCCESS.code, CommonResult.SUCCESS.message, data)

        /** 데이터 없는 성공 응답 */
        fun success(): ApiResponse<Unit?> =
            ApiResponse(CommonResult.SUCCESS.code, CommonResult.SUCCESS.message, null)

        /** Unit 반환 함수를 실행하고 데이터 없는 성공 응답을 반환한다. */
        fun successWith(action: () -> Unit): ApiResponse<Unit?> {
            action()
            return success()
        }

        /** 커스텀 메시지가 포함된 데이터 없는 성공 응답 */
        fun successWithMessage(message: String): ApiResponse<Unit?> =
            ApiResponse(CommonResult.SUCCESS.code, message, null)

        /** 목록 성공 응답 -- `List<E>`를 `ListData<E>`로 감싸 반환 */
        fun <E> page(list: List<E>): ApiResponse<ListData<E>> =
            ApiResponse(CommonResult.SUCCESS.code, CommonResult.SUCCESS.message, ListData(null, list))
    }
}
