package com.media.bus.reservation.contract.service

import com.media.bus.common.web.wrapper.PageResult
import com.media.bus.contract.security.MemberPrincipal
import com.media.bus.reservation.contract.dto.response.ContractResponse
import com.media.bus.reservation.reservation.service.StopResolutionService
import org.springframework.stereotype.Service
import java.util.*

/** 회원 본인 계약 조회를 조율하는 Facade */
@Service
class ContractFacade(
    private val stopResolutionService: StopResolutionService,
    private val contractService: ContractService,
) {
    /** 본인 계약 목록 조회 + 정류소 정보 결합. */
    fun getMyContracts(principal: MemberPrincipal, page: Int, size: Int): PageResult<ContractResponse> {
        val raw = contractService.getMyContracts(principal.id, page, size)
        val stopIds = raw.items.map { it.stopId }.toSet()
        val stopMap = stopResolutionService.resolveStops(stopIds)
        val enriched = raw.items.map { row ->
            val info = stopMap[row.stopId]
            row.copy(stopNumber = info?.stopId, stopName = info?.stopName)
        }
        return PageResult(
            items = enriched,
            totalCnt = raw.totalCnt,
            pageRows = raw.pageRows,
            pageNum = raw.pageNum,
        )
    }

    /** 본인 계약 단건 상세 + 정류소 정보 결합. 단건이어도 fallback을 위해 resolveStops 재사용. */
    fun getContractDetail(principal: MemberPrincipal, contractId: UUID): ContractResponse {
        val raw = contractService.getContractDetail(principal.id, contractId)
        val info = stopResolutionService.resolveStops(setOf(raw.stopId))[raw.stopId]
        return raw.copy(stopNumber = info?.stopId, stopName = info?.stopName)
    }
}
