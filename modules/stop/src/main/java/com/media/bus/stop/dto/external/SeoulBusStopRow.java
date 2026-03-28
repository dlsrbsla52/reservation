package com.media.bus.stop.dto.external;

import com.fasterxml.jackson.annotation.JsonProperty;

/// 서울 열린데이터광장 busStopLocationXyInfo API 단건 행(row) DTO
/// 참고: [...](http://openapi.seoul.go.kr:8088/){serviceKey}/json/busStopLocationXyInfo/{start}/{end}/
public record SeoulBusStopRow(

    @JsonProperty("STOPS_NO")
    String stopsNo,

    @JsonProperty("STOPS_NM")
    String stopsName,

    @JsonProperty("XCRD")
    String xCrd,

    @JsonProperty("YCRD")
    String yCrd,

    @JsonProperty("NODE_ID")
    String nodeId,

    @JsonProperty("STOPS_TYPE")
    String stopsType

) {}
