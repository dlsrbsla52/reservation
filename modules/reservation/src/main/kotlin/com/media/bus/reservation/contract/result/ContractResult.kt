package com.media.bus.reservation.contract.result

import com.media.bus.common.result.Result
import org.springframework.http.HttpStatus

/**
 * ## 계약(contract) 도메인 전용 결과 코드 Enum
 *
 * 예약 모듈의 `ReservationResult(R*)`와 구분하기 위해 `C` 접두사를 사용한다.
 * 공통 모듈의 `CommonResult`(00000~00299)와 충돌하지 않는다.
 */
enum class ContractResult(
    override val code: String,
    override val message: String,
    private val httpStatus: HttpStatus = HttpStatus.BAD_REQUEST,
) : Result {

    CONTRACT_NOT_FOUND("C0001", "계약 정보를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    CONTRACT_ACCESS_DENIED("C0002", "해당 계약에 접근할 권한이 없습니다.", HttpStatus.FORBIDDEN),
    ;

    override fun httpStatus(): HttpStatus = httpStatus
}
