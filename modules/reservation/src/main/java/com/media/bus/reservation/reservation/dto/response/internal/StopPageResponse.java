package com.media.bus.reservation.reservation.dto.response.internal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.media.bus.reservation.reservation.dto.response.StopInfo;

import java.util.List;

/// stop 서비스 내부 API의 페이지 응답 역직렬화용 DTO.
/// 설계 의도:
/// - stop 모듈 클래스를 직접 참조하지 않기 위해 reservation 내부 전용으로 선언합니다.
/// - 기존 StopServiceClient inner record에서 top-level public record로 분리하여
///   StopServiceClientImpl에서 재사용 가능하도록 합니다.
/// - 알 수 없는 필드는 무시하여 stop API 응답 변경에 유연하게 대응합니다.
@JsonIgnoreProperties(ignoreUnknown = true)
public record StopPageResponse(StopPageData data) {

    /// stop 내부 API 응답의 data 필드 구조.
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StopPageData(List<StopInfo> list) {}
}
