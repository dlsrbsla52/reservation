package com.media.bus.stop.service;

import com.media.bus.contract.security.MemberPrincipal;
import com.media.bus.stop.dto.request.SimpleStopCreateRequest;
import com.media.bus.stop.dto.request.StopSearchCriteria;
import com.media.bus.stop.dto.response.BusStopResponse;
import com.media.bus.stop.entity.Stop;
import com.media.bus.stop.guard.StopCommandGuard;
import com.media.bus.stop.repository.StopRepository;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;


@Slf4j
@Service
@RequiredArgsConstructor
public class StopService {

    private final StopRepository stopRepository;
    private final StopCommandGuard stopCommandGuard;

    /// 정류소 단건 수기 등록.
    /// Guard는 stopId 중복 등 비즈니스 규칙만 검증합니다.
    ///
    /// @param principal 인터셉터가 주입한 인증된 회원 정보
    /// @param request   등록 요청 DTO
    @Transactional
    public void createOneStop(MemberPrincipal principal, @Valid SimpleStopCreateRequest request) {
        stopCommandGuard.validateNotDuplicate(request.stopId());
        stopRepository.save(Stop.requestOf(request, principal.id()));
    }

    /// pk, stopId, stopName 중 하나의 기준으로 정류소 조회.
    /// - pk / stopId : unique 컬럼이므로 0\~1건 반환
    /// - stopName    : non-unique이므로 동명 정류소 다건 반환 가능
    public List<BusStopResponse> getBusStop(StopSearchCriteria criteria) {
        return switch (criteria) {
            case StopSearchCriteria.ByPk(var pk)         -> stopRepository.findById(pk).stream().map(BusStopResponse::of).toList();
            case StopSearchCriteria.ByStopId(var stopId) -> stopRepository.findByStopId(stopId).stream().map(BusStopResponse::of).toList();
            case StopSearchCriteria.ByStopName(var name) -> stopRepository.findByStopNameStartingWith(name).stream().map(BusStopResponse::of).toList();
        };
    }
}