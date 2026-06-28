package com.media.bus.iam.admin.facade

import com.media.bus.iam.admin.entity.CommissionRateHistoryEntity
import com.media.bus.iam.admin.entity.ContractCommissionOverrideEntity
import com.media.bus.iam.admin.entity.ManagerCommissionEntity
import com.media.bus.iam.admin.service.AdminCommissionService
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.util.*

/**
 * ## 영업사원 정산 비율 관리 Facade
 *
 * Controller와 Service 사이의 오케스트레이션 계층.
 * 현재 정산 비율 관리는 IAM DB만 사용하므로 S2S 호출 없이 Service를 위임한다.
 * 향후 S2S 호출이 추가될 경우 트랜잭션 외부(Facade)와 내부(Service)를 명시적으로 분리한다.
 */
@Service
class AdminManagerCommissionFacade(
    private val commissionService: AdminCommissionService,
) {

    /** 영업사원의 기본 정산 비율을 조회한다. */
    fun getManagerCommission(memberId: UUID): ManagerCommissionEntity =
        commissionService.getManagerCommission(memberId)

    /**
     * 영업사원의 기본 정산 비율을 변경한다.
     *
     * @param memberId 대상 영업사원 ID
     * @param newRate 새 정산 비율 (0 ~ 100)
     * @param changedBy 변경한 Master 회원 ID
     * @param reason 변경 사유 (선택)
     */
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

    /**
     * 계약별 정산 비율 오버라이드를 등록하거나 수정한다.
     *
     * @param contractId 대상 계약 ID
     * @param memberId 계약 담당 영업사원 ID
     * @param newRate 오버라이드 정산 비율 (0 ~ 100)
     * @param changedBy 변경한 Master 회원 ID
     * @param reason 변경 사유 (선택)
     */
    fun upsertContractCommissionOverride(
        contractId: UUID,
        memberId: UUID,
        newRate: BigDecimal,
        changedBy: UUID,
        reason: String? = null,
    ): ContractCommissionOverrideEntity =
        commissionService.upsertContractCommissionOverride(contractId, memberId, newRate, changedBy, reason)

    /**
     * 계약에 적용할 최종 정산 비율을 결정한다.
     * Reservation 모듈의 /internal S2S API에서 호출한다.
     */
    fun resolveEffectiveRate(memberId: UUID, contractId: UUID? = null): BigDecimal =
        commissionService.resolveEffectiveRate(memberId, contractId)

    /** 영업사원의 정산 비율 변경 이력 전체를 조회한다. */
    fun getCommissionHistory(memberId: UUID): List<CommissionRateHistoryEntity> =
        commissionService.getCommissionHistory(memberId)

    /** 특정 계약의 정산 비율 변경 이력을 조회한다. */
    fun getContractCommissionHistory(contractId: UUID): List<CommissionRateHistoryEntity> =
        commissionService.getContractCommissionHistory(contractId)
}
