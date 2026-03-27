package com.media.bus.reservation.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.media.bus.common.security.TokenProvider;
import com.media.bus.reservation.dto.response.StopInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

/**
 * stop 서비스 내부 API 클라이언트.
 *
 * 설계 의도:
 * - 유저 요청 컨텍스트와 무관하게 항상 S2S 토큰을 사용합니다.
 *   (internalRestClient는 유저 컨텍스트가 있으면 유저 JWT를 전파하므로 이 용도에 부적합)
 * - X-Service-Token 헤더에 S2S JWT를 주입 → stop의 S2STokenFilter 통과
 * - 호출 시마다 토큰을 새로 생성하여 만료 위험을 최소화합니다.
 */
@Slf4j
@Component
public class StopServiceClient {

    private static final String S2S_TOKEN_HEADER = "X-Service-Token";
    private static final String INTERNAL_STOP_PATH = "/api/v1/internal/stop";

    private final RestClient restClient;
    private final TokenProvider tokenProvider;

    public StopServiceClient(
            @Value("${services.stop.url}") String stopServiceUrl,
            TokenProvider tokenProvider
    ) {
        this.tokenProvider = tokenProvider;
        // stop 내부 API 전용 RestClient — 인증 헤더 자동 전파 없이 순수 HTTP 클라이언트로 사용
        this.restClient = RestClient.builder()
                .baseUrl(stopServiceUrl)
                .build();
    }

    /**
     * pk(UUID) 기준으로 정류소를 조회합니다.
     * 존재하지 않으면 빈 리스트를 반환합니다.
     */
    public List<StopInfo> getStopByPk(UUID pk) {
        log.debug("[StopServiceClient] pk 기준 정류소 조회: pk={}", pk);
        return fetch("pk", pk.toString());
    }

    /**
     * stopId(정류소 번호) 기준으로 정류소를 조회합니다.
     */
    public List<StopInfo> getStopByStopId(String stopId) {
        log.debug("[StopServiceClient] stopId 기준 정류소 조회: stopId={}", stopId);
        return fetch("stopId", stopId);
    }

    private List<StopInfo> fetch(String paramName, String paramValue) {
        StopPageResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(INTERNAL_STOP_PATH)
                        .queryParam(paramName, paramValue)
                        .build())
                .header(S2S_TOKEN_HEADER, tokenProvider.generateS2SToken())
                .retrieve()
                .body(StopPageResponse.class);

        if (response == null || response.data() == null) {
            return List.of();
        }
        return response.data().list() != null ? response.data().list() : List.of();
    }

    /**
     * stop 내부 API PageView 응답 역직렬화용 내부 레코드
     * stop 모듈 클래스를 직접 참조하지 않기 위해 클라이언트 내부에 선언
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record StopPageResponse(StopPageData data) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        private record StopPageData(List<StopInfo> list) {}
    }
}