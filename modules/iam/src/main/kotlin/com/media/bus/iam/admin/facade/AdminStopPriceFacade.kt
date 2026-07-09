package com.media.bus.iam.admin.facade

import com.media.bus.iam.client.stop.StopServiceClient
import com.media.bus.iam.client.stop.dto.StopPriceInfo
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.util.*

/**
 * ## 어드민 정류소 단가 조율 Facade
 *
 * 설계 의도:
 * - 어드민이 정류소 단가를 관리하기 위해 stop S2S 클라이언트를 조율한다.
 * - 로컬 DB 작업이 없는 순수 S2S 위임이므로 트랜잭션이 필요 없다.
 */
@Service
class AdminStopPriceFacade(
    private val stopServiceClient: StopServiceClient,
) {
    fun getStopPrice(stopId: UUID): StopPriceInfo? = stopServiceClient.getStopPrice(stopId)

    fun createStopPrice(stopId: UUID, unitPrice: BigDecimal, adminId: UUID): StopPriceInfo =
        stopServiceClient.createStopPrice(stopId, unitPrice, adminId)

    fun updateStopPrice(stopId: UUID, unitPrice: BigDecimal, adminId: UUID): StopPriceInfo =
        stopServiceClient.updateStopPrice(stopId, unitPrice, adminId)

    fun deleteStopPrice(stopId: UUID, adminId: UUID) =
        stopServiceClient.deleteStopPrice(stopId, adminId)
}
