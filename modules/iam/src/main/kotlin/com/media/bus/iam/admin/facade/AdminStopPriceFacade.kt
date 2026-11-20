package com.media.bus.iam.admin.facade

import com.media.bus.common.exceptions.BusinessException
import com.media.bus.common.web.wrapper.PageResult
import com.media.bus.iam.auth.result.AuthResult
import com.media.bus.iam.port.StopAdminPort
import com.media.bus.iam.port.dto.stop.*
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.util.*

/** 어드민 정류소 조회·단가 조율 Facade */
@Service
class AdminStopPriceFacade(
    private val stopAdminPort: StopAdminPort,
) {
    /** 정류소를 조회한다. */
    fun getStop(pk: UUID?, stopId: String?, stopName: String?): List<StopInfo> = when {
        pk != null -> stopAdminPort.getStopByPk(pk)
        stopId != null -> stopAdminPort.getStopByStopId(stopId)
        stopName != null -> stopAdminPort.getStopByStopName(stopName)
        else -> throw BusinessException(AuthResult.STOP_PRICE_LOOKUP_KEY_REQUIRED)
    }

    /** 검색 조건 없이 전체 정류소를 페이지네이션으로 조회한다. */
    fun getStopPage(page: Int, size: Int, keyword: String?, stopsType: String?): PageResult<StopInfo> =
        stopAdminPort.getStopPage(page, size, keyword, stopsType)

    fun getStopPrice(stopId: UUID): StopPriceInfo? = stopAdminPort.getStopPrice(stopId)

    fun createStopPrice(stopId: UUID, unitPrice: BigDecimal, adminId: UUID): StopPriceInfo =
        stopAdminPort.createStopPrice(stopId, unitPrice, adminId)

    fun updateStopPrice(stopId: UUID, unitPrice: BigDecimal, adminId: UUID): StopPriceInfo =
        stopAdminPort.updateStopPrice(stopId, unitPrice, adminId)

    fun deleteStopPrice(stopId: UUID, adminId: UUID) =
        stopAdminPort.deleteStopPrice(stopId, adminId)

    /** 지도 영역 내 정류소 마커를 조회한다. 좌표 검증은 stop 모듈이 수행한다. */
    fun getStopsInBounds(
        swLat: String,
        swLng: String,
        neLat: String,
        neLng: String,
        limit: Int?,
        stopsType: String?,
    ): StopBoundsInfo =
        stopAdminPort.getStopsInBounds(swLat, swLng, neLat, neLng, limit, stopsType)

    fun createSimpleStop(request: SimpleStopCreateRequest, adminId: UUID) =
        stopAdminPort.createSimpleStop(request, adminId)

    /** 공공 API 전체 정류소를 일괄 등록한다. 장시간 작업이다. */
    fun registerBulk(): StopBulkRegisterInfo =
        stopAdminPort.registerBulk()
}
