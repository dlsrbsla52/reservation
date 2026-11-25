package com.media.bus.content.result

import com.media.bus.common.result.Result
import org.springframework.http.HttpStatus

/** 홈페이지 콘텐츠 결과 코드(`W` 접두사) */
enum class ContentResult(
    override val code: String,
    override val message: String,
    private val httpStatus: HttpStatus = HttpStatus.BAD_REQUEST,
) : Result {

    PORTFOLIO_NOT_FOUND("W0001", "포트폴리오를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    NOTICE_NOT_FOUND("W0002", "공지사항을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    FAQ_NOT_FOUND("W0003", "FAQ를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    INQUIRY_NOT_FOUND("W0004", "문의를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    INVALID_PAGE_REQUEST("W0005", "page는 0 이상, size는 1~100, keyword는 100자 이하여야 합니다."),
    INVALID_CATEGORY("W0006", "알 수 없는 분류입니다."),
    INVALID_IMAGE_KEY("W0007", "올바르지 않은 이미지 키입니다."),
    IMAGE_NOT_UPLOADED("W0008", "이미지가 업로드되지 않았습니다. 업로드를 마친 뒤 다시 등록해주세요."),
    UNSUPPORTED_IMAGE_TYPE("W0009", "이미지는 webp, jpeg, png 형식만 올릴 수 있습니다."),
    IMAGE_TOO_LARGE("W0010", "이미지 용량이 허용 범위를 넘었습니다."),
    TOO_MANY_NOTICE_IMAGES("W0011", "공지사항 이미지는 최대 10장까지 등록할 수 있습니다."),
    INVALID_SORT_ORDER("W0012", "정렬할 항목 목록이 현재 목록과 일치하지 않습니다."),
    PRIVACY_CONSENT_REQUIRED("W0013", "개인정보 수집 및 이용에 동의해주세요."),
    INQUIRY_RATE_LIMITED("W0014", "문의 접수 요청이 너무 많습니다. 잠시 후 다시 시도해주세요.", HttpStatus.TOO_MANY_REQUESTS),
    INQUIRY_RATE_LIMIT_UNAVAILABLE("W0015", "문의 접수 호출 제한을 확인할 수 없습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    STORAGE_NOT_CONFIGURED("W0016", "이미지 저장소 설정이 없습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    STORAGE_ERROR("W0017", "이미지 저장소 요청에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_INQUIRY_STATUS("W0018", "알 수 없는 문의 처리 상태입니다."),
    IMAGE_KEY_IN_USE("W0019", "이미 다른 항목이 사용 중인 이미지입니다. 새로 업로드한 이미지를 사용해주세요.", HttpStatus.CONFLICT),
    INVALID_SEARCH_TYPE("W0020", "알 수 없는 검색 대상입니다."),
    ;

    override fun httpStatus(): HttpStatus = httpStatus
}
