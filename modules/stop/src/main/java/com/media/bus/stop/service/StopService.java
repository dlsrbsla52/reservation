package com.media.bus.stop.service;

import com.media.bus.common.web.wrapper.PageResult;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;


@Slf4j
@Service
@RequiredArgsConstructor
public class StopService {

    private final StopRepository stopRepository;
    private final StopCommandGuard stopCommandGuard;

    /**
     * 정류소 단건 수기 등록.
     * Guard는 stopId 중복 등 비즈니스 규칙만 검증합니다.
     *
     * @param principal 인터셉터가 주입한 인증된 회원 정보
     * @param request   등록 요청 DTO
     */
    @Transactional
    public void createOneStop(MemberPrincipal principal, @Valid SimpleStopCreateRequest request) {
        stopCommandGuard.isStopRegistered(request.stopId());
        stopRepository.save(Stop.requestOf(request, principal.id()));
    }

    public BusStopResponse getBusStop(String stopId) {
        return stopRepository.findByStopId(stopId)
            .map(BusStopResponse::of)
            .orElse(null);
    }

    /**
     * 정류소 이름 전방 일치 검색 (페이징).
     * idx_stop_name 인덱스를 활용하기 위해 'text%' 패턴만 사용한다.
     *
     * @param stopName 검색할 정류소 이름 접두사
     * @param pageable 페이지 요청 정보
     */
    public PageResult<BusStopResponse> getStopsByName(String stopName, Pageable pageable) {
        Page<Stop> page = stopRepository.findByStopNameStartingWith(stopName, pageable);
        return PageResult.<BusStopResponse>builder()
            .items(page.getContent().stream().map(BusStopResponse::of).toList())
            .totalCnt(page.getTotalElements())
            .pageRows(page.getSize())
            .pageNum(page.getNumber())
            .build();
    }
}
