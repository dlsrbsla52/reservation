package com.media.bus.iam.client.stop.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * ## stop 서비스 내부 페이지 조회 API(`/page`)의 역직렬화용 DTO
 *
 * 설계 의도:
 * - stop 측이 `PageResult` 형태(`items`/`totalCnt`/`pageRows`/`pageNum`)로 응답하므로
 *   목록 API(`StopPageResponse`, `ListData` 형태: `data.list`)와 응답 구조가 달라 별도로 선언한다.
 * - 알 수 없는 필드는 무시하여 stop API 응답 변경에 유연하게 대응한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class StopPagedApiResponse(val data: StopPagedData?) {

    /** stop 내부 페이지 조회 API 응답의 data 필드 구조 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class StopPagedData(
        val items: List<StopInfo>?,
        val totalCnt: Long?,
        val pageRows: Int?,
        val pageNum: Int?,
    )
}
