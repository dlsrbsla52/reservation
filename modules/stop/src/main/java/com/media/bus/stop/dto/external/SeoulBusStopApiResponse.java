package com.media.bus.stop.dto.external;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 서울 열린데이터광장 getBusStopInfo API 전체 응답 래퍼
 * 응답 구조:
 * {
 *   "busStopLocationXyInfo": {
 *     "list_total_count": 11237,
 *     "RESULT": { "CODE": "INFO-000", "MESSAGE": "정상 처리되었습니다" },
 *     "row": [ { ... } ]
 *   }
 * }
 */
public record SeoulBusStopApiResponse(

    @JsonProperty("busStopLocationXyInfo")
    Body busStopInfo

) {

    public record Body(

        @JsonProperty("list_total_count")
        int totalCount,

        @JsonProperty("RESULT")
        ApiResult result,

        @JsonProperty("row")
        List<SeoulBusStopRow> rows

    ) {}

    public record ApiResult(

        @JsonProperty("CODE")
        String code,

        @JsonProperty("MESSAGE")
        String message

    ) {

        private static final String SUCCESS_CODE = "INFO-000";

        public boolean isSuccess() {
            return SUCCESS_CODE.equals(code);
        }
    }
}
