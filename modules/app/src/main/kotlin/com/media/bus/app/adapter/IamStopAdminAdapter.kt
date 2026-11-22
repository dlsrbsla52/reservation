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

/** iam 관리자 기능 → stop 어댑터 */
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
        if (pks.isEmpty()) return emptyList()
        return callProvider { stopService.getBusStopsByIds(pks).map(::toStopInfo) }
    }

    override fun getStopPage(page: Int, size: Int, keyword: String?, stopsType: String?): PageResult<StopInfo> =
        callProvider {
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
            stopsType = parseCode(request.stopsType, "stopsType", StopType::fromName),
        )
        stopService.createOneStop(registeredById, stopRequest)
    }

    override fun registerBulk(): StopBulkRegisterInfo = callProvider {
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
