package com.media.bus.stop.service;

import com.media.bus.stop.dto.request.SimpleStopCreateRequest;
import com.media.bus.stop.dto.response.BusStopResponse;
import com.media.bus.stop.entity.Stop;
import com.media.bus.stop.repository.StopRepository;
import com.media.bus.stop.valid.StopModifyValiData;
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
    private final StopModifyValiData stopModifyValiData;

    @Transactional
    public void createOneStop(String token, @Valid SimpleStopCreateRequest request) {

        // 회원 권한 체크
        stopModifyValiData.isMemberAuthenticationAdmin(token);
        // 정류장 체크
        stopModifyValiData.isStopRegistered(request.stopId());

        stopRepository.save(Stop.requestOf(request));
    }

    @Transactional
    public BusStopResponse getBusStop(String stopId) {

        return stopRepository.findByStopId(stopId)
            .map(BusStopResponse::of)
            .orElseGet(null);
    }
}
