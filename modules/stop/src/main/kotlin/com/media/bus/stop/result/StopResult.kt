package com.media.bus.stop.result

import com.media.bus.common.result.Result
import com.media.bus.stop.entity.enums.StopType
import org.springframework.http.HttpStatus

/** 정류소 도메인 결과 코드(`S` 접두사). */
enum class StopResult(
    override val code: String,
    override val message: String,
    private val httpStatus: HttpStatus = HttpStatus.BAD_REQUEST,
) : Result {

    INVALID_LONGITUDE("S0001", "경도(xCrd) 값이 올바르지 않습니다."),
    INVALID_LATITUDE("S0002", "위도(yCrd) 값이 올바르지 않습니다."),
    BULK_REGISTER_IN_PROGRESS("S0003", "정류소 일괄 등록이 이미 진행 중입니다. 완료 후 다시 시도해주세요.", HttpStatus.CONFLICT),
    STOP_NAME_RESULT_LIMIT_EXCEEDED("S0004", "정류소 이름 조회는 최대 100건입니다. keyword와 페이지 조회를 사용해주세요."),
    INVALID_PAGE_REQUEST("S0005", "page는 0 이상, size는 1~100, keyword는 200자 이하여야 합니다."),
    STOP_NOT_FOUND("S0006", "정류소를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    STOP_PRICE_ALREADY_EXISTS("S0007", "이미 등록된 정류소 단가입니다.", HttpStatus.CONFLICT),
    STOP_PRICE_NOT_FOUND("S0008", "등록된 정류소 단가가 없습니다.", HttpStatus.NOT_FOUND),
    STOP_ALREADY_EXISTS("S0009", "이미 등록된 정류장입니다.", HttpStatus.CONFLICT),
    SEOUL_API_PARSE_FAIL("S0010", "서울 공공 API 응답을 파싱할 수 없습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    SEOUL_API_ERROR("S0011", "서울 공공 API가 오류를 반환했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_BOUNDS_LATITUDE("S0012", "swLat은 neLat보다 클 수 없습니다."),
    INVALID_BOUNDS_LONGITUDE("S0013", "swLng은 neLng보다 클 수 없습니다. (날짜변경선을 넘는 영역은 지원하지 않습니다)"),
    INVALID_STOP_NAME_KEYWORD("S0014", "정류소 이름은 공백이 아닌 200자 이하여야 합니다."),
    SEARCH_KEY_REQUIRED("S0015", "유효한 값을 전달해주세요."),
    UNKNOWN_STOP_TYPE("S0016", "알 수 없는 정류소 유형입니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_STOP_TYPE_FILTER("S0017", "알 수 없는 정류소 유형입니다. 가능한 값: " + StopType.entries.joinToString(", ") { it.name }),
    ;

    override fun httpStatus(): HttpStatus = httpStatus
}
