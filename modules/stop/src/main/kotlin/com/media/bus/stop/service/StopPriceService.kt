package com.media.bus.stop.service

import com.media.bus.stop.dto.response.StopPriceResponse
import com.media.bus.stop.entity.StopPriceEntity
import com.media.bus.stop.entity.StopPriceHistoryEntity
import com.media.bus.stop.entity.enums.StopPriceChangeType
import com.media.bus.stop.guard.StopPriceCommandGuard
import com.media.bus.stop.repository.StopPriceRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.*

@Service
class StopPriceService(
    private val stopPriceRepository: StopPriceRepository,
    private val stopPriceCommandGuard: StopPriceCommandGuard,
) {

    /** 정류소 단가 조회. 등록된 단가가 없으면 null을 반환한다 (미등록 상태를 예외로 취급하지 않음). */
    @Transactional(readOnly = true)
    fun getPrice(stopId: UUID): StopPriceResponse? =
        stopPriceRepository.findByStopId(stopId)?.let(StopPriceResponse::of)

    /**
     * 정류소 단가 신규 등록.
     * Guard가 정류소 존재 여부와 중복 등록 여부를 검증한 뒤 등록 이력을 함께 남긴다.
     */
    @Transactional
    fun createPrice(stopId: UUID, unitPrice: BigDecimal, registeredById: UUID?): StopPriceResponse {
        stopPriceCommandGuard.validateStopExists(stopId)
        stopPriceCommandGuard.validateNotDuplicate(stopId)

        val entity = StopPriceEntity.create(stopId, unitPrice, registeredById)
        StopPriceHistoryEntity.create(stopId, null, unitPrice, StopPriceChangeType.CREATE, registeredById)
        return StopPriceResponse.of(entity)
    }

    /** 정류소 단가 수정. 변경 전/후 단가를 이력에 함께 남긴다. */
    @Transactional
    fun updatePrice(stopId: UUID, unitPrice: BigDecimal, changedById: UUID?): StopPriceResponse {
        val entity = stopPriceCommandGuard.validateExists(stopId)
        val previousPrice = entity.unitPrice

        entity.updatePrice(unitPrice)
        StopPriceHistoryEntity.create(stopId, previousPrice, unitPrice, StopPriceChangeType.UPDATE, changedById)
        return StopPriceResponse.of(entity)
    }

    /** 정류소 단가 삭제. 삭제 전 단가를 이력에 남긴 뒤 레코드를 제거한다. */
    @Transactional
    fun deletePrice(stopId: UUID, changedById: UUID?) {
        val entity = stopPriceCommandGuard.validateExists(stopId)
        val previousPrice = entity.unitPrice

        StopPriceHistoryEntity.create(stopId, previousPrice, null, StopPriceChangeType.DELETE, changedById)
        entity.delete()
    }
}
