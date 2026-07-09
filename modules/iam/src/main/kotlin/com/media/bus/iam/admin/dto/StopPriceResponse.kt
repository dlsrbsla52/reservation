package com.media.bus.iam.admin.dto

import com.media.bus.iam.client.stop.dto.StopPriceInfo
import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.*

@Schema(description = "정류소 단가 응답")
data class StopPriceResponse(
    @param:Schema(description = "단가 pk", example = "018f1e2a-0000-7000-8000-000000000003")
    val id: UUID,
    @param:Schema(description = "정류소 pk(UUID)", example = "018f1e2a-0000-7000-8000-000000000001")
    val stopId: UUID,
    @param:Schema(description = "정류소 단가 (원)", example = "1200000.00")
    val unitPrice: BigDecimal,
    @param:Schema(description = "등록한 관리자 회원 ID", example = "018f1e2a-0000-7000-8000-000000000002")
    val registeredById: UUID?,
    @param:Schema(description = "생성일시", example = "2026-03-15 08:03:28.405709 +00:00")
    val createdAt: OffsetDateTime,
    @param:Schema(description = "수정일시", example = "2026-03-15 08:03:28.405709 +00:00")
    val updatedAt: OffsetDateTime,
) {
    companion object {
        fun of(info: StopPriceInfo): StopPriceResponse = StopPriceResponse(
            id = info.id,
            stopId = info.stopId,
            unitPrice = info.unitPrice,
            registeredById = info.registeredById,
            createdAt = info.createdAt,
            updatedAt = info.updatedAt,
        )
    }
}
