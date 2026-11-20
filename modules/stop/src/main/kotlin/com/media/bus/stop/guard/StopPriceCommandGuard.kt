package com.media.bus.stop.guard

import com.media.bus.common.exceptions.BusinessException
import com.media.bus.stop.entity.StopPriceEntity
import com.media.bus.stop.repository.StopPriceRepository
import com.media.bus.stop.repository.StopRepository
import com.media.bus.stop.result.StopResult
import org.springframework.stereotype.Service
import java.util.*

/**
 * ## 정류소 단가 명령 가드
 *
 * Repository 기반 비즈니스 규칙 검증만 수행한다 (인가 처리는 인터셉터가 담당).
 */
@Service
class StopPriceCommandGuard(
    private val stopRepository: StopRepository,
    private val stopPriceRepository: StopPriceRepository,
) {

    /** 단가 등록 대상 정류소가 존재하는지 검증한다. */
    fun validateStopExists(stopId: UUID) {
        stopRepository.findById(stopId)
            ?: throw BusinessException(StopResult.STOP_NOT_FOUND)
    }

    /** 동일 정류소에 이미 단가가 등록되어 있으면 예외를 던진다 (1:1 제약). */
    fun validateNotDuplicate(stopId: UUID) {
        stopPriceRepository.findByStopId(stopId)?.let {
            throw BusinessException(StopResult.STOP_PRICE_ALREADY_EXISTS)
        }
    }

    /** 수정/삭제 대상 단가가 존재하는지 검증하고 엔티티를 반환한다. */
    fun validateExists(stopId: UUID): StopPriceEntity =
        stopPriceRepository.findByStopId(stopId)
            ?: throw BusinessException(StopResult.STOP_PRICE_NOT_FOUND)
}
