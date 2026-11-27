package com.media.bus.reservation.contract.result

import com.media.bus.common.result.Result
import org.springframework.http.HttpStatus

/** 계약(contract) 도메인 전용 결과 코드 Enum */
enum class ContractResult(
    override val code: String,
    override val message: String,
    private val httpStatus: HttpStatus = HttpStatus.BAD_REQUEST,
) : Result {

    CONTRACT_NOT_FOUND("C0001", "계약 정보를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    CONTRACT_ACCESS_DENIED("C0002", "해당 계약에 접근할 권한이 없습니다.", HttpStatus.FORBIDDEN),
    CONTRACT_INVALID_PERIOD("C0003", "계약 종료일은 시작일 이후여야 합니다.", HttpStatus.BAD_REQUEST),
    CONTRACT_PERIOD_OVERLAP("C0004", "해당 정류소에 기간이 겹치는 계약이 이미 있습니다.", HttpStatus.CONFLICT),
    CONTRACT_KEYWORD_TOO_LONG("C0005", "keyword는 100자 이하여야 합니다."),
    CONTRACT_SEARCH_INVALID_PERIOD("C0006", "contractStartFrom은 contractStartTo 이후일 수 없습니다."),
    CONTRACT_PERIOD_OPTION_REQUIRED("C0007", "계약 종료일 또는 양수인 계약 개월 수 중 하나만 지정해야 합니다."),
    CONTRACT_STOP_PRICE_REQUIRED("C0008", "정류소 단가가 등록되어 있지 않습니다. 총 계약금액을 지정해주세요."),
    CONTRACT_INVALID_AMOUNT("C0009", "총 계약금액은 0보다 크고 정수 13자리, 소수 2자리 이하여야 합니다."),
    CONTRACT_INVALID_STATE_TRANSITION("C0010", "현재 계약 상태에서는 요청한 상태로 변경할 수 없습니다.", HttpStatus.CONFLICT),
    CONTRACT_INVALID_CANCEL_REASON("C0011", "취소 사유는 계약 취소 시에만 500자 이내로 입력할 수 있습니다."),
    CONTRACT_PAYMENT_DETAILS_REQUIRE_PAID("C0012", "납부 금액과 납부 일시는 납부완료 상태에서만 입력할 수 있습니다."),
    CONTRACT_INVALID_PAID_AMOUNT("C0013", "납부 금액은 0보다 크고 정수 13자리, 소수 2자리 이하여야 합니다."),
    CONTRACT_DETAIL_NOT_FOUND("C0014", "계약 납부 정보를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    ;

    override fun httpStatus(): HttpStatus = httpStatus
}
