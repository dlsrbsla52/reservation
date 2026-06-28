package com.media.bus.iam.admin.service

import com.media.bus.common.exceptions.BusinessException
import com.media.bus.iam.admin.entity.CommissionRateHistoryEntity
import com.media.bus.iam.admin.entity.ContractCommissionOverrideEntity
import com.media.bus.iam.admin.entity.ManagerCommissionEntity
import com.media.bus.iam.admin.entity.enums.CommissionChangeType
import com.media.bus.iam.admin.repository.CommissionRateHistoryRepository
import com.media.bus.iam.admin.repository.ContractCommissionOverrideRepository
import com.media.bus.iam.admin.repository.ManagerCommissionRepository
import com.media.bus.iam.auth.result.AuthResult
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.*

/**
 * ## 영업사원 정산 비율 서비스
 *
 * 커미션율 조회·수정·오버라이드·이력 조회를 담당한다.
 * 정산 비율 적용 우선순위: 계약별 오버라이드 → 영업사원 기본율 → 시스템 기본값(10%)
 */
@Service
class AdminCommissionService(
    private val managerCommissionRepository: ManagerCommissionRepository,
    private val contractCommissionOverrideRepository: ContractCommissionOverrideRepository,
    private val commissionRateHistoryRepository: CommissionRateHistoryRepository,
) {

    // ─────────────────────────────────────────────────────────────────
    // 영업사원 기본 정산 비율
    // ─────────────────────────────────────────────────────────────────

    /** 영업사원의 기본 정산 비율을 조회한다. 없으면 [AuthResult.COMMISSION_NOT_FOUND] 예외. */
    @Transactional(readOnly = true)
    fun getManagerCommission(memberId: UUID): ManagerCommissionEntity =
        managerCommissionRepository.findByMemberId(memberId)
            ?: throw BusinessException(AuthResult.COMMISSION_NOT_FOUND)

    /**
     * 영업사원 기본 정산 비율을 초기화한다.
     * 영업사원 역할 부여 시 기본값 10%로 생성한다.
     * 이미 존재하면 [AuthResult.COMMISSION_ALREADY_EXISTS] 예외.
     */
    @Transactional
    fun initManagerCommission(memberId: UUID): ManagerCommissionEntity {
        if (managerCommissionRepository.existsByMemberId(memberId)) {
            throw BusinessException(AuthResult.COMMISSION_ALREADY_EXISTS)
        }
        val entity = ManagerCommissionEntity.create(memberId)
        CommissionRateHistoryEntity.record(
            memberId = memberId,
            changeType = CommissionChangeType.DEFAULT_RATE,
            previousRate = null,
            newRate = entity.commissionRate,
            changedBy = memberId,
        )
        return entity
    }

    /**
     * 영업사원의 기본 정산 비율을 변경하고 이력을 기록한다.
     *
     * @param memberId 대상 영업사원 ID
     * @param newRate 새 정산 비율 (0 ~ 100)
     * @param changedBy 변경한 Master 회원 ID
     * @param reason 변경 사유 (선택)
     */
    @Transactional
    fun updateManagerCommissionRate(
        memberId: UUID,
        newRate: BigDecimal,
        changedBy: UUID,
        reason: String? = null,
    ): ManagerCommissionEntity {
        val entity = managerCommissionRepository.findByMemberId(memberId)
            ?: throw BusinessException(AuthResult.COMMISSION_NOT_FOUND)

        val previousRate = entity.commissionRate
        entity.commissionRate = newRate
        entity.updatedAt = OffsetDateTime.now(ZoneId.of("Asia/Seoul"))

        CommissionRateHistoryEntity.record(
            memberId = memberId,
            changeType = CommissionChangeType.DEFAULT_RATE,
            previousRate = previousRate,
            newRate = newRate,
            changedBy = changedBy,
            reason = reason,
        )
        return entity
    }

    // ─────────────────────────────────────────────────────────────────
    // 계약별 정산 비율 오버라이드
    // ─────────────────────────────────────────────────────────────────

    /** 계약의 정산 비율 오버라이드를 조회한다. 없으면 null 반환. */
    @Transactional(readOnly = true)
    fun getContractCommissionOverride(contractId: UUID): ContractCommissionOverrideEntity? =
        contractCommissionOverrideRepository.findByContractId(contractId)

    /**
     * 계약별 정산 비율 오버라이드를 등록하거나 수정하고 이력을 기록한다.
     * 해당 계약의 오버라이드가 없으면 새로 생성, 있으면 비율을 갱신한다.
     *
     * @param contractId 대상 계약 ID (reservation.contract.id 논리적 참조)
     * @param memberId 계약 담당 영업사원 ID
     * @param newRate 오버라이드 정산 비율 (0 ~ 100)
     * @param changedBy 변경한 Master 회원 ID
     * @param reason 변경 사유 (선택)
     */
    @Transactional
    fun upsertContractCommissionOverride(
        contractId: UUID,
        memberId: UUID,
        newRate: BigDecimal,
        changedBy: UUID,
        reason: String? = null,
    ): ContractCommissionOverrideEntity {
        val existing = contractCommissionOverrideRepository.findByContractId(contractId)
        val previousRate: BigDecimal?

        val entity = if (existing == null) {
            previousRate = null
            ContractCommissionOverrideEntity.create(
                contractId = contractId,
                memberId = memberId,
                commissionRate = newRate,
                updatedBy = changedBy,
            )
        } else {
            previousRate = existing.commissionRate
            existing.commissionRate = newRate
            existing.updatedBy = changedBy
            existing.updatedAt = OffsetDateTime.now(ZoneId.of("Asia/Seoul"))
            existing
        }

        CommissionRateHistoryEntity.record(
            memberId = memberId,
            changeType = CommissionChangeType.CONTRACT_OVERRIDE,
            previousRate = previousRate,
            newRate = newRate,
            changedBy = changedBy,
            contractId = contractId,
            reason = reason,
        )
        return entity
    }

    // ─────────────────────────────────────────────────────────────────
    // 정산 비율 결정 (Reservation S2S 내부 API 용)
    // ─────────────────────────────────────────────────────────────────

    /**
     * 계약에 적용할 최종 정산 비율을 결정한다.
     *
     * 우선순위:
     * 1. 계약별 오버라이드 (`contract_commission_override`)
     * 2. 영업사원 기본율 (`manager_commission`)
     * 3. 시스템 기본값 10%
     *
     * @param memberId 계약 담당 영업사원 ID
     * @param contractId 계약 ID (null이면 오버라이드 조회 생략)
     */
    @Transactional(readOnly = true)
    fun resolveEffectiveRate(memberId: UUID, contractId: UUID? = null): BigDecimal {
        if (contractId != null) {
            contractCommissionOverrideRepository.findByContractId(contractId)
                ?.let { return it.commissionRate }
        }
        return managerCommissionRepository.findByMemberId(memberId)?.commissionRate
            ?: DEFAULT_COMMISSION_RATE
    }

    // ─────────────────────────────────────────────────────────────────
    // 이력 조회
    // ─────────────────────────────────────────────────────────────────

    /** 영업사원의 정산 비율 변경 이력 전체를 최신순으로 조회한다. */
    @Transactional(readOnly = true)
    fun getCommissionHistory(memberId: UUID): List<CommissionRateHistoryEntity> =
        commissionRateHistoryRepository.findAllByMemberId(memberId)

    /** 특정 계약의 정산 비율 변경 이력을 최신순으로 조회한다. */
    @Transactional(readOnly = true)
    fun getContractCommissionHistory(contractId: UUID): List<CommissionRateHistoryEntity> =
        commissionRateHistoryRepository.findAllByContractId(contractId)

    companion object {
        /** 영업사원 기본 정산 비율 레코드가 없을 때 적용하는 시스템 기본값 */
        val DEFAULT_COMMISSION_RATE: BigDecimal = BigDecimal("10.00")
    }
}
