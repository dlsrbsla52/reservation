package com.media.bus.iam.admin.facade

import com.media.bus.common.exceptions.BusinessException
import com.media.bus.common.web.wrapper.PageResult
import com.media.bus.iam.auth.result.AuthResult
import com.media.bus.iam.port.StopAdminPort
import com.media.bus.iam.port.dto.stop.*
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.util.*

/**
 * ## 어드민 정류소 조회·단가 조율 Facade
 *
 * 설계 의도:
 * - 어드민이 정류소를 조회하고 단가를 관리하기 위해 `StopAdminPort`(stop 모듈)를 조율한다.
 * - 로컬 DB 작업이 없는 순수 위임이므로 트랜잭션을 두지 않는다. 트랜잭션은 stop 모듈 Service가 연다.
 */
@Service
class AdminStopPriceFacade(
    private val stopAdminPort: StopAdminPort,
) {
    /**
     * 정류소를 조회한다. pk(UUID) > stopId(정류소 번호) > stopName(이름) 순으로 우선 적용한다.
     * 셋 다 없으면 [BusinessException]. stopName은 동명 정류소 시 다건 반환한다.
     */
    fun getStop(pk: UUID?, stopId: String?, stopName: String?): List<StopInfo> = when {
        pk != null -> stopAdminPort.getStopByPk(pk)
        stopId != null -> stopAdminPort.getStopByStopId(stopId)
        stopName != null -> stopAdminPort.getStopByStopName(stopName)
        else -> throw BusinessException(AuthResult.STOP_PRICE_LOOKUP_KEY_REQUIRED)
    }

    /**
     * 검색 조건 없이 전체 정류소를 페이지네이션으로 조회한다.
     * keyword가 있으면 이름/번호 부분 일치로, stopsType이 있으면 해당 유형으로 필터링한다.
     */
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
