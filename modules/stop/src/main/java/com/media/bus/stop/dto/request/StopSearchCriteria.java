package com.media.bus.stop.dto.request;

import com.media.bus.common.exceptions.BusinessException;
import com.media.bus.common.result.type.CommonResult;

import java.util.UUID;

/// 정류소 단건 조회 검색 기준.
/// pk(UUID), stopId(정류소 번호), stopName(정류소 이름 완전 일치) 중 하나를 사용한다.
public sealed interface StopSearchCriteria
        permits StopSearchCriteria.ByPk, StopSearchCriteria.ByStopId, StopSearchCriteria.ByStopName {

    /// 대리키(UUID) 기준 조회
    record ByPk(UUID pk) implements StopSearchCriteria {}

    /// 정류소 번호(stop\_id) 기준 조회
    record ByStopId(String stopId) implements StopSearchCriteria {}

    /// 정류소 이름 전방 일치 조회 (idx\_stop\_name 인덱스 활용, 'text%' 패턴)
    record ByStopName(String stopName) implements StopSearchCriteria {}

    /// query param 세 값으로부터 검색 기준을 생성한다.
    /// 우선순위: pk > stopId > stopName. 모두 null이면 예외.
    static StopSearchCriteria of(UUID pk, String stopId, String stopName) {
        if (pk != null)       return new ByPk(pk);
        if (stopId != null)   return new ByStopId(stopId);
        if (stopName != null) return new ByStopName(stopName);
        throw new BusinessException(CommonResult.REQUEST_FAIL, "유효한 값을 전달해주세요.");
    }
}