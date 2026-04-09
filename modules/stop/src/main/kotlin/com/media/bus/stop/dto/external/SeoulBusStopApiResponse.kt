package com.media.bus.stop.dto.external

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * ## 서울 열린데이터광장 getBusStopInfo API 전체 응답 래퍼
 *
 * 응답 구조:
 * ```json
 * {
 *   "busStopLocationXyInfo": {
 *     "list_total_count": 11237,
 *     "RESULT": { "CODE": "INFO-000", "MESSAGE": "정상 처리되었습니다" },
 *     "row": [ { ... } ]
 *   }
 * }
 * ```
 */
data class SeoulBusStopApiResponse(
    @JsonProperty("busStopLocationXyInfo")
    val busStopInfo: Body?,
) {

    data class Body(
        @JsonProperty("list_total_count")
        val totalCount: Int,

        @JsonProperty("RESULT")
        val result: ApiResult?,

        @JsonProperty("row")
        val rows: List<SeoulBusStopRow>?,
    )

    data class ApiResult(
        @JsonProperty("CODE")
        val code: String,

        @JsonProperty("MESSAGE")
        val message: String,
    ) {
        fun isSuccess(): Boolean = SUCCESS_CODE == code

        companion object {
            private const val SUCCESS_CODE = "INFO-000"
        }
    }
}
