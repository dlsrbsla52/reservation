package com.media.bus.stop.guard

import com.media.bus.common.exceptions.BusinessException
import com.media.bus.stop.repository.StopRepository
import com.media.bus.stop.result.StopResult
import org.springframework.stereotype.Service

/** 정류소 명령 가드 */
@Service
class StopCommandGuard(
    private val stopRepository: StopRepository,
) {

    /** 동일 stopId가 이미 등록되어 있으면 예외를 던진다 (중복 등록 방지). */
    fun validateNotDuplicate(stopId: String) {
        stopRepository.findByStopId(stopId)?.let {
            throw BusinessException(StopResult.STOP_ALREADY_EXISTS)
        }
    }
}
