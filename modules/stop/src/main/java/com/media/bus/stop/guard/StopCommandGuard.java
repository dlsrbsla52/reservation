package com.media.bus.stop.guard;

import com.media.bus.common.exceptions.ServiceException;
import com.media.bus.stop.repository.StopRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * StopCommandGuard 구현체.
 *
 * JwtProvider 의존성을 제거하였습니다.
 * 인가 처리는 AuthorizeHandlerInterceptor가 담당하므로
 * Guard는 Repository 기반 비즈니스 규칙 검증만 수행합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StopCommandGuard {

    private final StopRepository stopRepository;

    public void isStopRegistered(String stopId) {
        stopRepository.findByStopId(stopId)
                .orElseThrow(() -> new ServiceException("등록된 정류장을 찾을 수 없습니다."));
    }
}
