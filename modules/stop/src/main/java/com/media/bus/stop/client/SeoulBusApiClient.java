package com.media.bus.stop.client;

import com.media.bus.common.exceptions.ServiceException;
import com.media.bus.stop.dto.external.SeoulBusStopApiResponse;
import com.media.bus.stop.dto.external.SeoulBusStopRow;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.List;

/**
 * 서울 열린데이터광장 버스 정류소 공공 API 클라이언트
 *
 * 사용 API: busStopLocationXyInfo
 * URL: http://openapi.seoul.go.kr:8088/{serviceKey}/json/busStopLocationXyInfo/{startIndex}/{endIndex}/
 *
 * API 키 발급: https://data.seoul.go.kr
 */
@Slf4j
@Component
public class SeoulBusApiClient {

    private static final int PAGE_SIZE = 1000;

    @Value("${seoul.api.key}")
    private String apiKey;

    @Value("${seoul.api.base-url:http://openapi.seoul.go.kr:8088}")
    private String baseUrl;

    // 외부 공공 API 호출용 — 인증 헤더 불필요, internalRestClient 사용하지 않음
    private RestClient restClient;

    @PostConstruct
    private void init() {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    /**
     * 전체 정류소 수를 조회한다 (1건만 요청해 totalCount 추출)
     */
    public int fetchTotalCount() {
        SeoulBusStopApiResponse response = call(1, 1);
        return response.busStopInfo().totalCount();
    }

    /**
     * startIndex ~ endIndex 범위의 정류소 목록을 조회한다 (1-based index)
     */
    public List<SeoulBusStopRow> fetchStops(int startIndex, int endIndex) {
        SeoulBusStopApiResponse response = call(startIndex, endIndex);
        List<SeoulBusStopRow> rows = response.busStopInfo().rows();
        return rows != null ? rows : Collections.emptyList();
    }

    public int getPageSize() {
        return PAGE_SIZE;
    }

    private SeoulBusStopApiResponse call(int start, int end) {
        log.debug("서울 공공 API 호출: start={}, end={}", start, end);

        SeoulBusStopApiResponse response = restClient.get()
                .uri("/{apiKey}/json/busStopLocationXyInfo/{start}/{end}/", apiKey, start, end)
                .retrieve()
                .body(SeoulBusStopApiResponse.class);

        if (response == null || response.busStopInfo() == null) {
            throw new ServiceException("서울 공공 API 응답을 파싱할 수 없습니다.");
        }

        SeoulBusStopApiResponse.ApiResult result = response.busStopInfo().result();
        if (result != null && !result.isSuccess()) {
            throw new ServiceException("서울 공공 API 오류: [" + result.code() + "] " + result.message());
        }

        return response;
    }
}
