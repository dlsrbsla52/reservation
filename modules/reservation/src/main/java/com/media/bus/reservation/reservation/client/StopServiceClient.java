package com.media.bus.reservation.reservation.client;

import com.media.bus.common.client.BaseServiceClient;
import com.media.bus.common.client.S2SRestClientFactory;
import com.media.bus.reservation.reservation.dto.response.StopInfo;
import com.media.bus.reservation.reservation.dto.response.internal.StopPageResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/// stop 서비스 내부 API 클라이언트.
/// 설계 의도:
/// - BaseServiceClient를 상속하여 HTTP 메서드(GET/POST/PUT/DELETE)를 직접 구성하지 않고
///   상위 클래스의 사전 정의된 메서드를 사용한다.
/// - S2SRestClientFactory.create()로 생성한 RestClient를 super()에 전달하므로
///   X-Service-Token 헤더는 팩토리 인터셉터가 자동으로 주입한다.
@Slf4j
@Component
public class StopServiceClient extends BaseServiceClient {

    private static final String STOP_PATH = "/api/v1/internal/stop";

    public StopServiceClient(
            @Value("${services.stop.url}") String stopServiceUrl,
            S2SRestClientFactory s2sRestClientFactory
    ) {
        // S2S 인터셉터가 적용된 RestClient를 상위 클래스에 전달
        super(s2sRestClientFactory.create(stopServiceUrl));
    }

    /// pk(UUID) 기준으로 정류소를 조회합니다.
    /// 존재하지 않으면 빈 리스트를 반환합니다.
    public List<StopInfo> getStopByPk(UUID pk) {
        log.debug("[StopServiceClient] pk 기준 정류소 조회: pk={}", pk);
        StopPageResponse response = get(
                uri -> uri.path(STOP_PATH).queryParam("pk", pk).build(),
                StopPageResponse.class
        );
        return extractList(response);
    }

    /// stopId(정류소 번호) 기준으로 정류소를 조회합니다.
    /// 존재하지 않으면 빈 리스트를 반환합니다.
    public List<StopInfo> getStopByStopId(String stopId) {
        log.debug("[StopServiceClient] stopId 기준 정류소 조회: stopId={}", stopId);
        StopPageResponse response = get(
                uri -> uri.path(STOP_PATH).queryParam("stopId", stopId).build(),
                StopPageResponse.class
        );
        return extractList(response);
    }

    // ── private ──────────────────────────────────────────────────────────────

    private List<StopInfo> extractList(StopPageResponse response) {
        if (response == null || response.data() == null) {
            return List.of();
        }
        return response.data().list() != null ? response.data().list() : List.of();
    }
}
