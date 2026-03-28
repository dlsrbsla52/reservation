package com.media.bus.stop.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/// 서울 공공 API 실제 연결 통합 테스트
/// - Spring 컨텍스트 없이 HTTP 클라이언트만 검증
/// - 실행 전 네트워크에서 openapi.seoul.go.kr:8088 접근 가능해야 함
class SeoulBusApiClientTest {

    private SeoulBusApiClient client;

    @BeforeEach
    void setUp() throws Exception {
        client = new SeoulBusApiClient();
        ReflectionTestUtils.setField(client, "apiKey", "53574a6e5976686b39314c67634559");
        ReflectionTestUtils.setField(client, "baseUrl", "http://openapi.seoul.go.kr:8088");
        ReflectionTestUtils.invokeMethod(client, "init");
    }

    @Test
    void fetchTotalCount_실제_API_연결_확인() {
        int totalCount = client.fetchTotalCount();

        assertThat(totalCount).isGreaterThan(0);
        System.out.println("총 정류소 수: " + totalCount);
    }

    @Test
    void fetchStops_첫번째_페이지_조회() {
        var stops = client.fetchStops(1, 10);

        assertThat(stops).isNotEmpty();
        assertThat(stops).hasSizeLessThanOrEqualTo(10);
        System.out.println("조회된 정류소: " + stops.size() + "개");
        stops.forEach(stop -> System.out.println("  - " + stop));
    }
}
