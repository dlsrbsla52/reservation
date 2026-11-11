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
    RESERVATION_INVALID_STATE_TRANSITION("R0006", "올바르지 않은 예약 상태 전이입니다."),
    // Redis 게이트는 선점자가 누구인지 모른 채 이 코드로 거절한다. 본인 재요청에도 맞도록 주어를 두지 않는다.
    STOP_ALREADY_HELD("R0007", "이미 예약이 진행 중인 정류소입니다.", HttpStatus.CONFLICT),
    STOP_UNDER_CONTRACT("R0008", "희망 계약 시작일에 이미 계약 중인 정류소입니다.", HttpStatus.CONFLICT),
    RESERVATION_LIMIT_EXCEEDED("R0009", "동시에 진행할 수 있는 예약 수를 초과했습니다.", HttpStatus.CONFLICT),
    RESERVATION_HOLD_NOT_EXTENDABLE("R0010", "진행 중인 예약만 선점 기간을 연장할 수 있습니다."),
    RESERVATION_HOLD_INVALID_EXPIRY("R0011", "선점 만료 일시는 현재 시각 이후여야 합니다."),
    ;

    override fun httpStatus(): HttpStatus = httpStatus
}
