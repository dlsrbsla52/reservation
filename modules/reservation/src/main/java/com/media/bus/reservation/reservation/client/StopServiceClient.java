package com.media.bus.reservation.reservation.client;

import com.media.bus.reservation.reservation.dto.response.StopInfo;
import com.media.bus.reservation.reservation.dto.response.internal.StopPageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/// stop 서비스 내부 API 클라이언트.
/// 설계 의도:
/// - StopApi(@HttpExchange 프록시)에 HTTP 호출을 위임하고,
///   응답 변환(extractList) 등 비즈니스 로직을 이 클래스에서 처리한다.
/// - 소비자(StopResolutionService)의 공개 API는 변경하지 않는다.
@Slf4j
@Component
@RequiredArgsConstructor
public class StopServiceClient {

    private final StopApi stopApi;

    /// pk(UUID) 기준으로 정류소를 조회합니다.
    /// 존재하지 않으면 빈 리스트를 반환합니다.
    public List<StopInfo> getStopByPk(UUID pk) {
        log.debug("[StopServiceClient] pk 기준 정류소 조회: pk={}", pk);
        StopPageResponse response = stopApi.getStopByPk(pk);
        return extractList(response);
    }

    /// stopId(정류소 번호) 기준으로 정류소를 조회합니다.
    /// 존재하지 않으면 빈 리스트를 반환합니다.
    public List<StopInfo> getStopByStopId(String stopId) {
        log.debug("[StopServiceClient] stopId 기준 정류소 조회: stopId={}", stopId);
        StopPageResponse response = stopApi.getStopByStopId(stopId);
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
