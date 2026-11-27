package com.media.bus.reservation.contract.service

import com.media.bus.common.exceptions.BusinessException
import com.media.bus.common.web.wrapper.PageResult
import com.media.bus.reservation.contract.dto.request.CreateContractRequest
import com.media.bus.reservation.contract.dto.response.ContractResponse
import com.media.bus.reservation.contract.entity.ContractDetailEntity
import com.media.bus.reservation.contract.entity.ContractEntity
import com.media.bus.reservation.contract.repository.ContractRepository
import com.media.bus.reservation.contract.result.ContractResult
import com.media.bus.reservation.reservation.dto.response.StopInfo
import com.media.bus.reservation.slot.StopSlot
import com.media.bus.reservation.support.SQLSTATE_EXCLUSION_VIOLATION
import com.media.bus.reservation.support.flushTranslating
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.*

/** 계약 도메인의 DB 저장을 담당하는 서비스 */
@Service
class ContractService(
    private val contractRepository: ContractRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Contract와 ContractDetail을 함께 저장한다. */
    @Transactional
    fun createContract(
        stopInfo: StopInfo,
        request: CreateContractRequest,
        slotNo: Short = StopSlot.DEFAULT_NO,
    ): ContractEntity {
        val endDate = resolveEndDate(request)
        if (!endDate.isAfter(request.contractStartDate) || endDate.year > 9999 || request.contractStartDate.year < 1) {
            throw BusinessException(ContractResult.CONTRACT_INVALID_PERIOD)
        }
        if (contractRepository.existsOccupyingOverlap(
                stopInfo.id, slotNo, request.contractStartDate, endDate,
            )
        ) {
            throw BusinessException(ContractResult.CONTRACT_PERIOD_OVERLAP)
        }
        val totalAmount = request.totalAmount ?: calculateTotalAmount(stopInfo, request, endDate)
        if (totalAmount.signum() <= 0 || totalAmount.scale() > 2 || totalAmount >= BigDecimal.TEN.pow(13)) {
            throw BusinessException(ContractResult.CONTRACT_INVALID_AMOUNT)
        }
        val contract = ContractEntity.create(stopInfo, request, slotNo, endDate)
        ContractDetailEntity.create(contract, request, totalAmount)
        flushTranslating(SQLSTATE_EXCLUSION_VIOLATION) {
            BusinessException(ContractResult.CONTRACT_PERIOD_OVERLAP)
        }

        log.debug(
            "[ContractService] 계약 저장 완료: contractId={}, memberId={}, stopId={}",
            contract.id.value, request.memberId, stopInfo.id,
        )
        return contract
    }

    private fun resolveEndDate(request: CreateContractRequest): OffsetDateTime {
        val endDate = request.contractEndDate
        val months = request.durationMonths
        if (endDate != null && months == null) return endDate
        if (endDate == null && months != null && months > 0) {
            return request.contractStartDate.withOffsetSameInstant(KST).plusMonths(months.toLong())
        }
        throw BusinessException(ContractResult.CONTRACT_PERIOD_OPTION_REQUIRED)
    }

    private fun calculateTotalAmount(
        stopInfo: StopInfo,
        request: CreateContractRequest,
        endDate: OffsetDateTime,
    ): BigDecimal {
        val unitPrice = stopInfo.unitPrice ?: throw BusinessException(ContractResult.CONTRACT_STOP_PRICE_REQUIRED)
        request.durationMonths?.let { return unitPrice.multiply(it.toBigDecimal()) }

        val start = request.contractStartDate.withOffsetSameInstant(KST)
        val end = endDate.withOffsetSameInstant(KST)
        val calendarMonths = ChronoUnit.MONTHS.between(YearMonth.from(start), YearMonth.from(end))
        val fullMonths = if (start.plusMonths(calendarMonths).isAfter(end)) calendarMonths - 1 else calendarMonths
        val remainderStart = start.plusMonths(fullMonths)
        val remainderSeconds = Duration.between(remainderStart, end).seconds
        val monthSeconds = Duration.between(remainderStart, start.plusMonths(fullMonths + 1)).seconds
        val remainderAmount = unitPrice.multiply(remainderSeconds.toBigDecimal())
            .divide(monthSeconds.toBigDecimal(), 2, RoundingMode.HALF_UP)
        return unitPrice.multiply(fullMonths.toBigDecimal()).add(remainderAmount)
    }

    /** 특정 시점에 광고면을 점유 중인 계약(PENDING/ACTIVE)이 있는지 확인한다. */
    @Transactional(readOnly = true)
    fun isSlotOccupiedAt(stopId: UUID, slotNo: Short, at: OffsetDateTime): Boolean =
        contractRepository.existsOccupyingAt(stopId, slotNo, at)

    /** 본인 계약 목록 조회 (페이지네이션). */
    @Transactional(readOnly = true)
    fun getMyContracts(memberId: UUID, page: Int, size: Int): PageResult<ContractResponse> {
        val contracts = contractRepository.findByMemberIdPaged(memberId, page, size)
        val totalCnt = contractRepository.countByMemberId(memberId)

        val items = contracts.map { ContractResponse.from(it) }
        return PageResult(
            items = items,
            totalCnt = totalCnt,
            pageRows = size,
            pageNum = page,
        )
    }

    /** 본인 계약 단건 상세 조회. */
    @Transactional(readOnly = true)
    fun getContractDetail(memberId: UUID, contractId: UUID): ContractResponse {
        val contract = contractRepository.findById(contractId)
            ?: throw BusinessException(ContractResult.CONTRACT_NOT_FOUND)

        if (contract.memberId != memberId) {
            throw BusinessException(ContractResult.CONTRACT_ACCESS_DENIED)
        }

        return ContractResponse.from(contract)
    }

    companion object {
        private val KST = ZoneOffset.ofHours(9)
    }
}
