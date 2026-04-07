package com.media.bus.common.result;

import org.springframework.http.HttpStatus;

import java.io.Serializable;
import java.util.function.UnaryOperator;

public interface Result extends Serializable {

    String getCode();

    String getMessageId();

    String getMessage();

    String getMessage(UnaryOperator<String> operator, String id);

    /// 이 Result 코드에 대응하는 HTTP 상태 코드.
    ///
    /// 기본값은 400 Bad Request이며, 각 구현체(enum)가 의미에 맞게 오버라이드한다.
    /// `BusinessException`이 이 값을 읽어 HTTP 응답 상태를 결정하므로,
    /// 호출부에서 `HttpStatus`를 직접 지정하지 않아도 된다.
    default HttpStatus httpStatus() {
        return HttpStatus.BAD_REQUEST;
    }
}
