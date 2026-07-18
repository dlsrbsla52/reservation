package com.media.bus.iam.admin.facade

import com.media.bus.common.exceptions.BusinessException
import com.media.bus.common.result.type.CommonResult
import com.media.bus.iam.client.stop.StopServiceClient
import com.media.bus.iam.client.stop.dto.StopInfo
import com.media.bus.iam.client.stop.dto.StopPriceInfo
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.util.*

/**
 * ## 어드민 정류소 조회·단가 조율 Facade
 *
 * 설계 의도:
 * - 어드민이 정류소를 조회하고 단가를 관리하기 위해 stop S2S 클라이언트를 조율한다.
 * - 로컬 DB 작업이 없는 순수 S2S 위임이므로 트랜잭션이 필요 없다.
 */
@Service
class AdminStopPriceFacade(
    private val stopServiceClient: StopServiceClient,
) {
    /**
     * 정류소를 조회한다. pk(UUID) > stopId(정류소 번호) > stopName(이름) 순으로 우선 적용한다.
     * 셋 다 없으면 [BusinessException]. stopName은 동명 정류소 시 다건 반환한다.
     */
    fun getStop(pk: UUID?, stopId: String?, stopName: String?): List<StopInfo> = when {
        pk != null -> stopServiceClient.getStopByPk(pk)
        stopId != null -> stopServiceClient.getStopByStopId(stopId)
        stopName != null -> stopServiceClient.getStopByStopName(stopName)
        else -> throw BusinessException(CommonResult.REQUEST_FAIL, "pk, stopId, stopName 중 하나는 필수입니다.")
    }

    fun getStopPrice(stopId: UUID): StopPriceInfo? = stopServiceClient.getStopPrice(stopId)

    fun createStopPrice(stopId: UUID, unitPrice: BigDecimal, adminId: UUID): StopPriceInfo =
        stopServiceClient.createStopPrice(stopId, unitPrice, adminId)

    fun updateStopPrice(stopId: UUID, unitPrice: BigDecimal, adminId: UUID): StopPriceInfo =
        stopServiceClient.updateStopPrice(stopId, unitPrice, adminId)

    fun deleteStopPrice(stopId: UUID, adminId: UUID) =
        stopServiceClient.deleteStopPrice(stopId, adminId)
}
