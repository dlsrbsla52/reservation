package com.media.bus.reservation.reservation.result

import com.media.bus.common.result.Result
import org.springframework.http.HttpStatus

/**
 * ## 예약(reservation) 모듈 전용 결과 코드 Enum
 *
 * 공통 모듈의 `CommonResult`(00000~00299)와 충돌을 피하기 위해 `R` 접두사를 사용한다.
 * 비즈니스 흐름에서 발생하는 검증 실패/상태 오류를 표현한다.
 */
enum class ReservationResult(
    override val code: String,
    override val message: String,
    private val httpStatus: HttpStatus = HttpStatus.BAD_REQUEST,
) : Result {

    RESERVATION_NOT_FOUND("R0001", "예약 정보를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    RESERVATION_ACCESS_DENIED("R0002", "해당 예약에 접근할 권한이 없습니다.", HttpStatus.FORBIDDEN),
    RESERVATION_ALREADY_EXISTS("R0003", "해당 정류소에 이미 진행 중인 예약이 있습니다.", HttpStatus.CONFLICT),
    RESERVATION_NOT_CANCELLABLE("R0004", "상담 전(PENDING) 상태에서만 예약을 취소할 수 있습니다."),
    RESERVATION_STATE_MISSING("R0005", "예약 상태 정보를 확인할 수 없습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    ;

    override fun httpStatus(): HttpStatus = httpStatus
}
