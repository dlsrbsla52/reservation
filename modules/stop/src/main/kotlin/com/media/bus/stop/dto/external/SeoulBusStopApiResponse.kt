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
    @param:JsonProperty("busStopLocationXyInfo")
    val busStopInfo: Body?,
) {

    data class Body(
        @param:JsonProperty("list_total_count")
        val totalCount: Int,

        @param:JsonProperty("RESULT")
        val result: ApiResult?,

        @param:JsonProperty("row")
        val rows: List<SeoulBusStopRow>?,
    )

    data class ApiResult(
        @param:JsonProperty("CODE")
        val code: String,

        @param:JsonProperty("MESSAGE")
        val message: String,
    ) {
        fun isSuccess(): Boolean = SUCCESS_CODE == code

        companion object {
            private const val SUCCESS_CODE = "INFO-000"
        }
    }
}
