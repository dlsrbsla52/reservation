package com.media.bus.common.result

import org.springframework.http.HttpStatus
import java.io.Serializable

/**
 * ## 작업 결과 인터페이스
 *
 * 모든 Result enum이 구현해야 하는 공통 계약.
 */
interface Result : Serializable {

    val code: String

    val messageId: String

    val message: String

    fun getMessage(operator: (String) -> String, id: String): String

    /**
     * 이 Result 코드에 대응하는 HTTP 상태 코드.
     *
     * 기본값은 400 Bad Request이며, 각 구현체(enum)가 의미에 맞게 오버라이드한다.
     * `BusinessException`이 이 값을 읽어 HTTP 응답 상태를 결정하므로,
     * 호출부에서 `HttpStatus`를 직접 지정하지 않아도 된다.
     */
    fun httpStatus(): HttpStatus = HttpStatus.BAD_REQUEST
}
