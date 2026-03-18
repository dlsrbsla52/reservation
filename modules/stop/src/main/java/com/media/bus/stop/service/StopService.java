package com.media.bus.stop.service;


import com.media.bus.stop.dto.request.SimpleStopCreateRequest;
import com.media.bus.stop.entity.Stop;
import com.media.bus.stop.repository.StopRepository;
import com.media.bus.stop.valid.StopModifyValiDate;
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
    private final StopModifyValiDate stopModifyValiDate;

    @Transactional
    public void createOneStop(String token, @Valid SimpleStopCreateRequest request) {

        stopModifyValiDate.isMemberAuthenticationAdmin(token);

        stopRepository.save(Stop.requestOf(request));
    }
}
