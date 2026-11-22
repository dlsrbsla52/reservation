package com.media.bus.iam.admin.facade

import com.media.bus.iam.admin.entity.CommissionRateHistoryEntity
import com.media.bus.iam.admin.entity.ContractCommissionOverrideEntity
import com.media.bus.iam.admin.entity.ManagerCommissionEntity
import com.media.bus.iam.admin.service.AdminCommissionService
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.util.*

/** 영업사원 정산 비율 관리 Facade */
@Service
class AdminManagerCommissionFacade(
    private val commissionService: AdminCommissionService,
) {

    /** 영업사원의 기본 정산 비율을 조회한다. */
    fun getManagerCommission(memberId: UUID): ManagerCommissionEntity =
        commissionService.getManagerCommission(memberId)

    /** 영업사원의 기본 정산 비율을 변경한다. */
    fun updateManagerCommissionRate(
        memberId: UUID,
        newRate: BigDecimal,
        changedBy: UUID,
        reason: String? = null,
    ): ManagerCommissionEntity =
        commissionService.updateManagerCommissionRate(memberId, newRate, changedBy, reason)

    /** 계약의 정산 비율 오버라이드를 조회한다. */
    fun getContractCommissionOverride(contractId: UUID): ContractCommissionOverrideEntity? =
        commissionService.getContractCommissionOverride(contractId)

    /** 계약별 정산 비율 오버라이드를 등록하거나 수정한다. */
    fun upsertContractCommissionOverride(
        contractId: UUID,
        memberId: UUID,
        newRate: BigDecimal,
        changedBy: UUID,
        reason: String? = null,
    ): ContractCommissionOverrideEntity =
        commissionService.upsertContractCommissionOverride(contractId, memberId, newRate, changedBy, reason)

    /** 영업사원의 정산 비율 변경 이력 전체를 조회한다. */
    fun getCommissionHistory(memberId: UUID): List<CommissionRateHistoryEntity> =
        commissionService.getCommissionHistory(memberId)

    /** 특정 계약의 정산 비율 변경 이력을 조회한다. */
    fun getContractCommissionHistory(contractId: UUID): List<CommissionRateHistoryEntity> =
        commissionService.getContractCommissionHistory(contractId)
}
