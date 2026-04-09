package com.media.bus.stop.guard

import com.media.bus.common.exceptions.BusinessException
import com.media.bus.stop.repository.StopRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service

/**
 * ## 정류소 명령 가드
 *
 * JwtProvider 의존성을 제거하였습니다.
 * 인가 처리는 `AuthorizeHandlerInterceptor`가 담당하므로
 * Guard는 Repository 기반 비즈니스 규칙 검증만 수행합니다.
 */
@Service
class StopCommandGuard(
    private val stopRepository: StopRepository,
) {

    /**
     * 동일 stopId가 이미 등록되어 있으면 예외를 던진다 (중복 등록 방지).
     * 신규 등록 시 호출하며, 존재하지 않는 경우가 정상이다.
     */
    fun validateNotDuplicate(stopId: String) {
        stopRepository.findByStopId(stopId)?.let {
            throw BusinessException(HttpStatus.CONFLICT, "이미 등록된 정류장입니다.")
        }
    }
}
