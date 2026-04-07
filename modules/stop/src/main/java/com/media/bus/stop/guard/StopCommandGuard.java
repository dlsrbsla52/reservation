package com.media.bus.stop.guard;

import com.media.bus.common.exceptions.BusinessException;
import org.springframework.http.HttpStatus;
import com.media.bus.stop.repository.StopRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/// StopCommandGuard 구현체.
/// JwtProvider 의존성을 제거하였습니다.
/// 인가 처리는 AuthorizeHandlerInterceptor가 담당하므로
/// Guard는 Repository 기반 비즈니스 규칙 검증만 수행합니다.
@Slf4j
@Service
@RequiredArgsConstructor
public class StopCommandGuard {

    private final StopRepository stopRepository;

    /// 동일 stopId가 이미 등록되어 있으면 예외를 던진다 (중복 등록 방지).
    /// 신규 등록 시 호출하며, 존재하지 않는 경우가 정상이다.
    public void validateNotDuplicate(String stopId) {
        stopRepository.findByStopId(stopId).ifPresent(s -> {
            throw new BusinessException(HttpStatus.CONFLICT, "이미 등록된 정류장입니다.");
        });
    }
}
