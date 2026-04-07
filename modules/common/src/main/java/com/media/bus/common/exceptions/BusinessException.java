package com.media.bus.common.exceptions;

import com.media.bus.common.result.Result;
import com.media.bus.common.result.type.CommonResult;
import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.io.Serial;

/// 예상된 비즈니스 실패를 표현하는 예외.
///
/// 입력 검증 실패, 리소스 미존재, 중복 등 정상 비즈니스 흐름의 일부로 발생하는 상황에 사용한다.
/// `ServiceException`(기술적 오류, HTTP 500)과 달리 4xx로 응답되며, 모니터링 알람을 발동시키지 않는다.
///
/// ## HTTP 상태 결정 방식
/// `Result` 구현체(enum)가 `httpStatus()`를 통해 HTTP 상태를 선언하므로,
/// 호출부에서 `HttpStatus`를 직접 지정할 필요가 없다.
///
/// ```java
/// // 올바른 사용법 — HTTP 상태는 CommonResult.USER_NOT_FOUND_FAIL이 결정(404)
/// throw new BusinessException(CommonResult.USER_NOT_FOUND_FAIL);
///
/// // Result 코드 없이 메시지만 전달할 경우 — HTTP 400 기본값
/// throw new BusinessException(HttpStatus.NOT_FOUND, "등록된 정류장을 찾을 수 없습니다.");
/// ```
@SuppressWarnings("unused")
public class BusinessException extends BaseException {

    @Serial
    private static final long serialVersionUID = 7381893246823945120L;

    /// HTTP 응답 상태 코드. `Result.httpStatus()`에서 자동으로 파생된다.
    @Getter
    private final HttpStatus httpStatus;

    // --- Result 기반 생성자 (권장) ---

    public BusinessException(Result result) {
        super(result);
        this.httpStatus = result.httpStatus();
    }

    public BusinessException(Result result, String message) {
        super(result, message);
        this.httpStatus = result.httpStatus();
    }

    public BusinessException(Result result, Throwable cause) {
        super(result, cause);
        this.httpStatus = result.httpStatus();
    }

    // --- Result 없이 메시지만 전달하는 경우 (특정 Result 코드가 없을 때) ---

    /// HTTP 상태를 400 Bad Request로 기본 지정하는 편의 생성자.
    public BusinessException(String message) {
        this(HttpStatus.BAD_REQUEST, message);
    }

    /// Result 없이 HTTP 상태와 메시지만으로 생성하는 편의 생성자.
    /// 별도 Result 코드가 정의되지 않은 경우에 한해 사용한다.
    public BusinessException(HttpStatus httpStatus, String message) {
        super(CommonResult.FAIL, message);
        this.httpStatus = httpStatus;
    }
}