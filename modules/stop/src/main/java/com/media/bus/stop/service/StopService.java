package com.media.bus.stop.service;

import com.media.bus.contract.security.MemberPrincipal;
import com.media.bus.stop.dto.request.SimpleStopCreateRequest;
import com.media.bus.stop.dto.response.BusStopResponse;
import com.media.bus.stop.entity.Stop;
import com.media.bus.stop.guard.StopCommandGuard;
import com.media.bus.stop.repository.StopRepository;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


@Slf4j
@Service
@RequiredArgsConstructor
public class StopService {
    
    private final StopRepository stopRepository;
    private final StopCommandGuard stopCommandGuard;

    @Transactional
    public void createOneStop(String token, @Valid SimpleStopCreateRequest request) {

        MemberPrincipal principal = stopCommandGuard.isMemberAuthenticationAdmin(token);
        stopCommandGuard.isStopRegistered(request.stopId());

        stopRepository.save(Stop.requestOf(request, principal.id()));
    }

    @Transactional
    public BusStopResponse getBusStop(String stopId) {

        return stopRepository.findByStopId(stopId)
            .map(BusStopResponse::of)
            .orElse(null);
    }
}
