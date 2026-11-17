package com.media.bus.app.adapter

import com.media.bus.common.web.wrapper.PageResult
import com.media.bus.iam.port.StopAdminPort
import com.media.bus.iam.port.dto.stop.*
import com.media.bus.stop.dto.request.StopSearchCriteria
import com.media.bus.stop.dto.response.BusStopResponse
import com.media.bus.stop.dto.response.StopPriceResponse
import com.media.bus.stop.entity.enums.StopType
import com.media.bus.stop.map.dto.StopBoundsCriteria
import com.media.bus.stop.map.service.StopMapQueryService
import com.media.bus.stop.service.StopPriceService
import com.media.bus.stop.service.StopRegisterService
import com.media.bus.stop.service.StopService
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.util.*
import com.media.bus.stop.dto.request.SimpleStopCreateRequest as StopSimpleStopCreateRequest

/**
 * ## iam 관리자 기능 → stop 어댑터
 *
 * 예전 `InternalStopController`가 위임하던 stop 서비스를 직접 부른다. 입력 검증은 제공자 진입점에 있다
 * (`StopService.getBusStopsByIds`·`createOneStop`, `StopPriceService`, `StopSearchCriteria.of`, `StopBoundsCriteria.of`).
 *
 * DTO는 필드별로 명시해 변환한다. 예전 JSON 왕복은 이름이 같은 필드를 자동으로 옮겼지만, 여기서는 기본값이 있는
 * 필드(예: `StopInfo.unitPrice`)를 빠뜨려도 컴파일되므로 `IamStopAdminAdapterTest`가 변환 결과를 고정한다.
 */
@Component
class IamStopAdminAdapter(
    private val stopService: StopService,
    private val stopPriceService: StopPriceService,
    private val stopMapQueryService: StopMapQueryService,
    private val stopRegisterService: StopRegisterService,
) : StopAdminPort {

    override fun getStopByPk(pk: UUID): List<StopInfo> = search(StopSearchCriteria.of(setOf(pk), null, null))

    override fun getStopByStopId(stopId: String): List<StopInfo> = search(StopSearchCriteria.of(null, stopId, null))

    override fun getStopByStopName(stopName: String): List<StopInfo> = search(StopSearchCriteria.of(null, null, stopName))

    override fun getStopsByPks(pks: Collection<UUID>): List<StopInfo> {
        // 빈 입력은 호출하지 않는다. 제공자는 빈 목록을 400으로 거절한다.
        if (pks.isEmpty()) return emptyList()
        return callProvider { stopService.getBusStopsByIds(pks).map(::toStopInfo) }
    }

    override fun getStopPage(page: Int, size: Int, keyword: String?, stopsType: String?): PageResult<StopInfo> =
        callProvider {
            // stopsType 문자열 검증은 StopService 가 StopType.parseFilter 로 한다(알 수 없는 값 → 400)
            val result = stopService.getBusStopPage(page, size, keyword, stopsType)
            PageResult(
                items = result.items.map(::toStopInfo),
                totalCnt = result.totalCnt,
                pageRows = result.pageRows,
                pageNum = result.pageNum,
            )
        }

    override fun getStopPrice(stopId: UUID): StopPriceInfo? =
        callProvider { stopPriceService.getPrice(stopId)?.let(::toPriceInfo) }

    override fun createStopPrice(stopId: UUID, unitPrice: BigDecimal, registeredById: UUID): StopPriceInfo =
        callProvider { toPriceInfo(stopPriceService.createPrice(stopId, unitPrice, registeredById)) }

    override fun updateStopPrice(stopId: UUID, unitPrice: BigDecimal, changedById: UUID): StopPriceInfo =
        callProvider { toPriceInfo(stopPriceService.updatePrice(stopId, unitPrice, changedById)) }

    override fun deleteStopPrice(stopId: UUID, changedById: UUID) =
        callProvider { stopPriceService.deletePrice(stopId, changedById) }

    override fun getStopsInBounds(
        swLat: String,
        swLng: String,
        neLat: String,
        neLng: String,
        limit: Int?,
        stopsType: String?,
    ): StopBoundsInfo = callProvider {
        // 좌표·limit·유형 검증은 StopBoundsCriteria.of 가 한다(공개 지도 API 와 같은 규칙)
        val result = stopMapQueryService.findMarkersInBounds(
            StopBoundsCriteria.of(swLat, swLng, neLat, neLng, limit, stopsType),
        )
        StopBoundsInfo(
            items = result.items.map {
                StopMarkerInfo(
                    id = it.id,
                    stopId = it.stopId,
                    stopName = it.stopName,
                    lat = it.lat,
                    lng = it.lng,
                    stopsType = it.stopsType.name,
                )
            },
            truncated = result.truncated,
            limit = result.limit,
        )
    }

    override fun createSimpleStop(request: SimpleStopCreateRequest, registeredById: UUID) = callProvider {
        val stopRequest = StopSimpleStopCreateRequest(
            stopId = request.stopId,
            stopName = request.stopName,
            xCrd = request.xCrd,
            yCrd = request.yCrd,
            nodeId = request.nodeId,
            // 예전에는 JSON 역직렬화가 잘못된 유형을 400 으로 거절했다
            stopsType = parseCode(request.stopsType, "stopsType", StopType::fromName),
        )
        stopService.createOneStop(registeredById, stopRequest)
    }

    override fun registerBulk(): StopBulkRegisterInfo = callProvider {
        // 서울 공공 API 실패(ServiceException)는 callProvider 가 예전과 같은 500/00100 으로 바꾼다
        val result = stopRegisterService.registerAllFromPublicApi()
        StopBulkRegisterInfo(
            savedCount = result.savedCount,
            updatedCount = result.updatedCount,
            skippedCount = result.skippedCount,
            failedCount = result.failedCount,
            totalCount = result.totalCount,
        )
    }

    private fun search(criteria: StopSearchCriteria): List<StopInfo> =
        callProvider { stopService.getBusStop(criteria).map(::toStopInfo) }

    private fun toStopInfo(stop: BusStopResponse) = StopInfo(
        id = stop.id,
        stopId = stop.stopId,
        stopName = stop.stopName,
        xCrd = stop.xCrd,
        yCrd = stop.yCrd,
        nodeId = stop.nodeId,
        stopsType = stop.stopsType,
        createdAt = stop.createdAt,
        updatedAt = stop.updatedAt,
        unitPrice = stop.unitPrice,
    )

    private fun toPriceInfo(price: StopPriceResponse) = StopPriceInfo(
        id = price.id,
        stopId = price.stopId,
        unitPrice = price.unitPrice,
        registeredById = price.registeredById,
        createdAt = price.createdAt,
        updatedAt = price.updatedAt,
    )
}
